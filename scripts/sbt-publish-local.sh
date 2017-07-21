#!/bin/bash -x
if ! which sdk > /dev/null; then
    curl -s "https://get.sdkman.io" | bash
    export SDKMAN_DIR="~/.sdkman"
    [[ -s "~/.sdkman/bin/sdkman-init.sh" ]] && source "~/.sdkman/bin/sdkman-init.sh"
    sdk install scala
    sdk install sbt
fi

sdk publishLocal
