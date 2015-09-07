#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [[ $DIR != $(pwd) ]]
then
    echo "You must run this script from the folder yuzu/scala"
    exit
fi

sbt "run-main com.github.jmccrae.yuzu.RDFBackend $@"
