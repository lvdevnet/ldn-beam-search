#!/usr/bin/env bash

SIMULATION=SearchPhrase
#SIMULATION=SearchUnexisting

HOST=elixir
# Elixir
PORT=4000
# go/redis
# PORT=4080
# go/memory
# PORT=4088
# scala
# PORT=8080

URL=${HOST}:${PORT}

WARMUP_FROM=1
WARMUP_TO=50
WARMUP_DURING=5

FROM=10
TO=200
DURING=30

mvn gatling:execute \
    -Dgatling.simulationClass=simulations.${SIMULATION} \
    -Durl="$URL" \
    -DwarmRampFrom=${WARMUP_FROM} \
    -DwarmRampTo=${WARMUP_TO} \
    -DwarmRampDuring=${WARMUP_FROM} \
    -DrampFrom=${FROM} \
    -DrampTo=${TO} \
    -DrampDuring=${DURING}

