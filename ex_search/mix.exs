defmodule ExSearch.Mixfile do
  use Mix.Project

  def project do
    [app: :ex_search,
     version: "0.0.1",
     elixir: "~> 1.0",
     deps: deps,
     aliases: aliases]
  end

  def application do
    [applications: [:cowboy, :plug, :logger]]
  end

  defp deps do
    [{:cowboy, "~> 1.0.3"},
     {:plug, "~> 1.0.1"},
     {:poolboy, "~> 1.5.1"},
     {:exredis, "~> 0.2.0"}]
  end

  defp aliases do
    [r: "run --no-halt -e ExSearch.main"]
  end
end
