defmodule ExSearch.Redis do
  import Exredis.Api

  def start do
    {:ok, redis} = Exredis.start_link
    redis
  end

  @token   "t:"
  @content "c:"

  def find_docs(words) do
    redis = defaultclient # where am I supposed to store it?
    redis |> sinter(Enum.map(words, fn(str) -> @token <> String.downcase(str) end))
    |> Enum.map(fn(id) -> { id, redis |> get(@content <> id) } end)
  end

end
