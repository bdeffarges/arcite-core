package com.actelion.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging._
import com.actelion.research.arcite.core.eventinfo.{EventInfoLogging, ExpLog, LogCategory, LogType}
import com.actelion.research.arcite.core.experiments.LocalExperiments.{LoadExperiment, SaveExperimentFailed, SaveExperimentSuccessful}
import com.actelion.research.arcite.core.experiments.ManageExperimentActors.StartExperimentsServiceActors
import com.actelion.research.arcite.core.fileservice.FileServiceActor
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{getClass => _, _}
import com.actelion.research.arcite.core.rawdata.DefineRawData
import com.actelion.research.arcite.core.rawdata.DefineRawData.{GetExperimentForRawDataSet, RawDataSetFailed, RawDataSetWithRequesterAndExperiment}
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperimentsWithRequester, IndexExperiment, RemoveFromIndex, SearchForXResultsWithRequester}
import com.actelion.research.arcite.core.transforms.TransformDoneSuccess
import com.actelion.research.arcite.core.utils
import com.actelion.research.arcite.core.utils.{FoldersHelpers, FullName, Owner, WriteFeedbackActor}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by Bernard Deffarges on 06/03/16.
  */

class ManageExperiments(eventInfoLoggingAct: ActorRef) extends Actor with ArciteJSONProtocol with ActorLogging {

  import ManageExperiments._

  val config = ConfigFactory.load()

  val filePath = config.getString("arcite.snapshot")

  val path = Paths.get(filePath)

  val fileServiceAct = context.system.actorOf(FileServiceActor.props(), "file_service")

  val luceneRAMSearchAct = context.system.actorOf(Props(classOf[ArciteLuceneRamIndex]), "experiments_lucene_index")

  private var experiments: Map[String, Experiment] = LocalExperiments.loadAllLocalExperiments()

  experiments.values.foreach(exp ⇒ luceneRAMSearchAct ! IndexExperiment(exp))

  import StandardOpenOption._

  import spray.json._

  import scala.collection.convert.wrapAsScala._

  override def receive = {

    case AddExperiment(exp) ⇒
      self ! AddExperimentWithRequester(exp, sender())


    case AddExperimentWithRequester(exp, requester) ⇒
      if (!experiments.contains(exp.uid)) {
        experiments += ((exp.uid, exp))

        LocalExperiments.saveExperiment(exp) match {

          case SaveExperimentSuccessful(expLog) ⇒
            eventInfoLoggingAct ! AddLog(exp, ExpLog(LogType.CREATED, LogCategory.SUCCESS, "experiment created. ", Some(exp.uid)))
            requester ! AddedExperiment(exp.uid)

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingExperiment(error)
        }

        luceneRAMSearchAct ! IndexExperiment(exp)

      } else {
        requester ! FailedAddingExperiment(s"same experiment ${exp.owner.organization}/${exp.name} already exists. ")
      }


    case CloneExperimentWithRequester(cexp, requester) ⇒
      val origExp = experiments.get(cexp.originExp)
      if (origExp.isEmpty) {
        requester ! FailedAddingExperiment(s"could not find original experiment ")
      } else {
        val newExp = origExp.get.copy(name = cexp.cloneExpProps.name,
          description = cexp.cloneExpProps.description, owner = cexp.cloneExpProps.owner, state = ExpState.NEW)

        experiments += ((newExp.uid, newExp))

        LocalExperiments.saveExperiment(newExp) match {

          case SaveExperimentSuccessful(expLog) ⇒
            // linking all data, ...
            val orVis = ExperimentFolderVisitor(origExp.get)
            val tgrVis = ExperimentFolderVisitor(newExp)

            FoldersHelpers.deepLinking(orVis.rawFolderPath, tgrVis.rawFolderPath)
            FoldersHelpers.deepLinking(orVis.userMetaFolderPath, tgrVis.userMetaFolderPath)

            eventInfoLoggingAct ! AddLog(newExp, ExpLog(LogType.CREATED, LogCategory.SUCCESS,
              s"cloned experiment [${origExp.get.uid}] ", Some(newExp.uid)))

            requester ! AddedExperiment(newExp.uid)

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingExperiment(error)
        }

        luceneRAMSearchAct ! IndexExperiment(newExp)

      }


    case DeleteExperimentWithRequester(digest, requester) ⇒
      val exp = experiments.get(digest)

      if (exp.isEmpty) {
        requester ! ExperimentDeleteFailed(s"experiment [$digest] does not exist.")
      } else if (exp.get.state.eq(ExpState.NEW) && !ExperimentFolderVisitor(exp.get).isImmutableExperiment()) {
        experiments -= digest
        luceneRAMSearchAct ! RemoveFromIndex(exp.get)
        requester ! LocalExperiments.safeDeleteExperiment(exp.get)
      } else {
        requester ! ExperimentDeleteFailed(s"experiment [$digest] can not be deleted, it's immutable. ")
      }


    case AddDesignWithRequester(design, requester) ⇒
      val uid = design.experiment

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val nexp = exp.get.copy(design = design.design)
        experiments += ((uid, nexp))

        LocalExperiments.saveExperiment(nexp) match {

          case SaveExperimentSuccessful(expL) ⇒
            requester ! AddedDesignSuccess
            luceneRAMSearchAct ! IndexExperiment(nexp)

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingDesign(error)
        }
      } else {
        requester ! FailedAddingDesign("Experiment does not exist")
      }


    case AddExpPropertiesWithRequester(addProps, requester) ⇒
      val uid = addProps.exp

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val ex = exp.get
        val nex = ex.copy(properties = ex.properties ++ addProps.properties)
        experiments += ((uid, nex))
        LocalExperiments.saveExperiment(nex) match {

          case SaveExperimentSuccessful(expL) ⇒
            luceneRAMSearchAct ! IndexExperiment(nex)
            requester ! AddedPropertiesSuccess

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingProperties(error)
        }
      } else {
        requester ! FailedAddingProperties("Experiment does not exist")
      }


    case galex: GetAllExperimentsWithRequester ⇒
      log.info(s"asking ManageExperiments for ${galex.max} experiments starting page ${galex.page}... to ${galex.requester}")

      val start = galex.page * galex.max
      val end = start + galex.max

      val defExpLog = ExpLog(LogType.UNKNOWN, LogCategory.UNKNOWN, "no latest log. ", utils.almostTenYearsAgo)


      def readLastExpLog(exp: Experiment): ExpLog = {
        val f = ExperimentFolderVisitor(exp).lastUpdateLog
        if (f.toFile.exists) {
          try {
            Files.readAllLines(f).mkString("\n").parseJson.convertTo[ExpLog]
          } catch {
            case e: Exception ⇒ defExpLog
          }
        } else {
          defExpLog
        }
      }

      val allExps = experiments.values.map(exp ⇒ (exp, readLastExpLog(exp)))
        .toList.sortBy(_._2.date).reverse
        .slice(start, end)
        .map(e ⇒ ExperimentSummary(e._1.name, e._1.description, e._1.owner,
          e._1.uid, utils.getDateAsStrg(e._2.date)))

      galex.requester ! AllExperiments(allExps)


    case LoadExperiment(folder: String) ⇒
      val expCon = LocalExperiments.loadExperiment(Paths.get(folder))
      sender() ! expCon


    case s: SearchForXResultsWithRequester ⇒
      luceneRAMSearchAct ! s


    case FoundExperimentsWithRequester(foundExperiments, requester) ⇒
      log.debug(s"found ${foundExperiments.experiments.size} experiments ")
      val resp = foundExperiments.experiments.map(f ⇒ experiments(f.digest))
        .map(f ⇒ ExperimentSummary(f.name, f.description, f.owner, f.uid))
      requester ! SomeExperiments(resp.size, resp)


    case GetExperimentWithRequester(digest, requester) ⇒
      log.debug(s"retrieving experiment with digest: $digest")
      val exp = experiments.get(digest)
      if (exp.isDefined) {
        val ex = LocalExperiments.loadExperiment(ExperimentFolderVisitor(exp.get).experimentFilePath)
        if (ex.isDefined) requester ! ExperimentFound(ex.get)
        else requester ! NoExperimentFound
      } else {
        requester ! NoExperimentFound
      }


    case GetExperiment(digest) ⇒
      self forward GetExperimentWithRequester(digest, sender())


    case rdsw: GetExperimentForRawDataSet ⇒
      val uid = rdsw.rdsr.rds.experiment
      log.debug(s"retrieving experiment with uid: $uid")
      val exp = experiments.get(uid)
      if (exp.isDefined) {
        sender() ! RawDataSetWithRequesterAndExperiment(rdsw.rdsr, exp.get)
      } else {
        rdsw.rdsr.requester ! RawDataSetFailed(error = s"could not find exp for uid=${uid}")
      }


    case GetAllExperimentsLastUpdate ⇒
      sender() ! AllLastUpdatePath(experiments.values.map(ExperimentFolderVisitor(_).lastUpdateLog).toSet)


    case GetAllExperimentsMostRecentLogs ⇒
      val logs = experiments.values
        .flatMap(ExperimentFolderVisitor(_).logsFolderPath.toFile.listFiles()
          .filter(_.getName.startsWith("log_"))).map(_.toPath)
      sender() ! AllExperimentLogsPath(logs.toSet)


    case GetAllTransforms(experiment) ⇒
      val allTransforms = getAllTransforms(experiment)
      sender ! TransformsForExperiment(allTransforms)


    case GetTransfDefFromExpAndTransf(experiment, transform) ⇒
      val transDef = getTransfDefFromExpAndTransf(experiment, transform)
      sender() ! transDef


    case mf: MoveUploadedFile ⇒
      import StandardCopyOption._
      log.debug("move uploaded file to right place. ")
      val exp = experiments.get(mf.experiment)
      if (exp.isDefined) {
        val v = ExperimentFolderVisitor(exp.get)
        val fp = Paths.get(mf.filePath)
        mf match {
          case MoveMetaFile(_, _) ⇒
            Files.copy(fp, v.userMetaFolderPath resolve fp.getFileName, REPLACE_EXISTING)
          case MoveRawFile(_, _) ⇒
            Files.copy(fp, v.userRawFolderPath resolve fp.getFileName, REPLACE_EXISTING)
        }
        Files.delete(fp)
        Files.delete(fp.getParent)
      }


    case addProps: AddExpPropertiesWithRequester ⇒ //todo implement


    case grf: GetRawFiles ⇒
      logger.info("looking for raw data files list")
      val actRef = sender()
      val exp = experiments.get(grf.experiment)
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromRawFolder(exp.get)), actRef)
      } else {
        sender() ! FolderFilesInformation(Set())
      }


    case gmf: GetMetaFiles ⇒
      logger.info("looking for meta data files list")
      val exp = experiments.get(gmf.experiment)
      val actRef = sender()
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromMetaFolder(exp.get)), actRef)
      } else {
        sender() ! FolderFilesInformation(Set())
      }


    case readLogs: ReadLogs ⇒
      val exp = experiments.get(readLogs.experiment)
      if (exp.isEmpty) {
        sender()  ! InfoLogs(List())
      } else {
        val eFV = ExperimentFolderVisitor(exp.get)

        import EventInfoLogging._ //todo should catch / raise exceptions
        val latestLogs = InfoLogs(eFV.logsFolderPath.toFile.listFiles()
          .filter(f ⇒ f.getName.startsWith("log_"))
          .map(f ⇒ readLog(f.toPath))
          .filter(_.isDefined).map(_.get)
          .sortBy(_.date).slice(readLogs.page * readLogs.max,
          (readLogs.page + 1) * readLogs.max).toList)

        sender() ! latestLogs
      }


    case makeImmutable: MakeImmutable ⇒
      val exper = experiments.get(makeImmutable.experiment)
      if (exper.isDefined) {
        val exp = exper.get
        LocalExperiments.saveExperiment(exp) match {

          case SaveExperimentSuccessful(expLog) ⇒
            eventInfoLoggingAct ! AddLog(exp, ExpLog(LogType.UPDATED,
              LogCategory.SUCCESS, "experiment is immutable.", Some(exp.uid)))

          case SaveExperimentFailed(error) ⇒
            eventInfoLoggingAct ! AddLog(exp, ExpLog(LogType.UPDATED,
              LogCategory.ERROR, "experiment is immutable failed.", Some(exp.uid)))
        }

        experiments += ((exper.get.uid, exper.get.copy(state = ExpState.IMMUTABLE)))
        Files.write(ExperimentFolderVisitor(exper.get).immutableStateFile,
          "IMMUTABLE".getBytes(StandardCharsets.UTF_8), CREATE)
      }


    case any: Any ⇒ log.debug(s"don't know what to do with this message $any")
  }

  def getAllTransforms(experiment: String): Set[TransformDoneSuccess] = {
    val exp = experiments(experiment)

    val transfF = ExperimentFolderVisitor(exp).transformFolderPath

    import spray.json._

    transfF.toFile.listFiles().filter(_.isDirectory)
      .map(d ⇒ Paths.get(d.getAbsolutePath, WriteFeedbackActor.FILE_NAME))
      .filter(p ⇒ p.toFile.exists())
      .map(p ⇒ Files.readAllLines(p).toList.mkString("\n").parseJson.convertTo[TransformDoneSuccess]).toSet
  }

  def getTransfDefFromExpAndTransf(experiment: String, transform: String): FoundTransfDefFullName = {

    val exp = experiments(experiment)
    val ef = ExperimentFolderVisitor(exp).transformFolderPath

    import spray.json._

    //todo check whether it exists...
    val f = Paths.get(ef.toString, transform, WriteFeedbackActor.FILE_NAME)
    val tdi = Files.readAllLines(f).toList.mkString("\n").parseJson.convertTo[TransformDoneSuccess]

    FoundTransfDefFullName(tdi.transformDefinition)
  }
}


object ManageExperiments {

  val logger = LoggerFactory.getLogger(getClass)

  case class State(experiments: Set[Experiment] = Set())


  case class AddExperiment(experiment: Experiment)

  case class AddExperimentWithRequester(experiment: Experiment, requester: ActorRef)


  case class CloneExperimentNewProps(name: String, description: String, owner: Owner)

  case class CloneExperiment(originExp: String, cloneExpProps: CloneExperimentNewProps)

  case class CloneExperimentWithRequester(cloneExperiment: CloneExperiment, requester: ActorRef)


  case class AddDesign(experiment: String, design: ExperimentalDesign)

  case class AddDesignWithRequester(addDesign: AddDesign, requester: ActorRef)


  case class AddExpProps(properties: Map[String, String])

  case class AddExpProperties(exp: String, properties: Map[String, String])

  case class AddExpPropertiesWithRequester(addProps: AddExpProperties, requester: ActorRef)

  case class Experiments(exps: Set[Experiment])

  case class GetAllTransforms(experiment: String)

  case class TransformsForExperiment(transforms: Set[TransformDoneSuccess])

  case class GetTransfDefFromExpAndTransf(experiment: String, transform: String)

  case class FoundTransfDefFullName(fullName: FullName)

  case object GetAllExperimentsLastUpdate

  case class AllLastUpdatePath(paths: Set[Path])

  case object GetAllExperimentsMostRecentLogs

  case class AllExperimentLogsPath(paths: Set[Path])

  case class MakeImmutable(experiment: String)

}

class ManageExperimentActors extends Actor with ActorLogging {

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 50, withinTimeRange = 1 minute) {
      case _: FileSystemException ⇒ Restart // todo in both case should log
      case _: Exception ⇒ Restart // todo should eventually escalate
    }

  override def receive: Receive = {

    case StartExperimentsServiceActors ⇒
      val eventInfoLoggingAct = context.actorOf(Props(classOf[EventInfoLogging]), "event_logging_info")
      val manExpActor = context.actorOf(Props(classOf[ManageExperiments], eventInfoLoggingAct), "experiments_manager")
      val defineRawDataAct = context.actorOf(Props(classOf[DefineRawData], manExpActor), "define_raw_data")
      log.info(s"event info log: [$eventInfoLoggingAct]")
      log.info(s"exp manager actor: [$manExpActor]")
      log.info(s"raw data define: [$defineRawDataAct]")

      import context.dispatcher

      import scala.concurrent.duration._

      context.system.scheduler.schedule(45 seconds, 10 minutes) {
        eventInfoLoggingAct ! BuildRecentLastUpdate
        eventInfoLoggingAct ! BuildRecentLogs
      }
  }

  def startActorSystemForExperiments(): Unit = {
  }
}

object ManageExperimentActors {
  val actSystem = ActorSystem("experiments-actor-system", config.getConfig("experiments-manager"))

  val topActor = actSystem.actorOf(Props(classOf[ManageExperimentActors]), "exp_actors_manager")

  case object StartExperimentsServiceActors

  def startExperimentActorSystem(): Unit = topActor ! StartExperimentsServiceActors
}