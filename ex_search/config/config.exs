use Mix.Config

config :logger, :console,
  level: :debug,
  format: "$time [$level] $levelpad$message $metadata\n",
  metadata: [:query_string]
