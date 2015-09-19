defmodule ExSearch.Redis do
  use Supervisor

  def start_link do
    Supervisor.start_link(__MODULE__, [])
  end

  def init([]) do
    pool_opts = [
      name: {:local, :exredis_poolboy},
      worker_module: Exredis,
      size: 110,
      max_overflow: 1000,
    ]
    children = [
      :poolboy.child_spec(:exredis_poolboy, pool_opts)
    ]
    supervise(children, strategy: :one_for_one, name: __MODULE__)
  end

  @token   "t:"
  @content "c:"

  def find_docs(words) do
    :poolboy.transaction(:exredis_poolboy, &find_docs(&1, words))
  end

  def find_docs(redis, words) do
    import Exredis.Api
    ids = redis |> sinter(Enum.map(words, fn(str) -> @token <> String.downcase(str) end))
    titles = redis |> mget(Enum.map(ids, fn(str) -> @content <> str end))
    Enum.zip(ids, titles)
  end

end
