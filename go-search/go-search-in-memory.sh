#!/bin/sh
exec ./go-search -articles ../dump-converter/txt/ -limit 100000 -cutoff 50 #-memory 768
