"use strict";

let express = require('express');
let app = express();
let Redis = require('ioredis');
let redis = new Redis({host: '127.0.0.1'});
var cluster = require('cluster');
var cpuCount = require('os').cpus().length;

if (cluster.isMaster) {
    // Create a worker for each CPU
    for (var i = 0; i < cpuCount; i += 1) {
        cluster.fork();
    }

    // Listen for dying workers
    cluster.on('exit', function (worker) {
        // Replace the dead worker, we're not sentimental
        console.log('Worker ' + worker.id + ' died :(');
        cluster.fork();
    });
} else {
    app.get('/content', (req, res) => {
        let q = req.query.q;

        let words = q.split(" ");
        let keys = words.map((word) => {return "t:" + word});

        redis.sinter(keys).then((ids) => {
            if (ids.length == 0) {
                res.send("");
            } else {
                let goodIds = ids.map((id) => {return "c:" + id});

                redis.mget(goodIds).then((result) => {
                    res.send(result
                            .map((title, i) => {return ids[i] + "," + title})
                            .join("\n"));
                });
            }
        });
    });

    let server = app.listen(5001, () => {
        let host = server.address().address;
        let port = server.address().port;

        console.log('App listening at http://%s:%s', host, port);
    });
}
