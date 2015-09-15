defmodule ExSearch.Redis do
  # where am I supposed to store it?
  def start do
    {:ok, redis} = Exredis.start_link
    redis
  end

  @token   "t:"
  @content "c:"

  def find_docs(words) do
    import Exredis.Api
    redis = defaultclient
# prone to crash
# ** (exit) an exception was raised:
#     ** (ArgumentError) argument error
#         :erlang.register(:exredis_hapi_default_client, #PID<0.249.0>)
#         (elixir) lib/process.ex:278: Process.register/2
#         lib/exredis/api.ex:34: Exredis.Api.defaultclient/0
#         (ex_search) lib/ex_search/redis.ex:12: ExSearch.Redis.find_docs/1
#         (ex_search) lib/ex_search/http.ex:23: anonymous fn/1 in ExSearch.HTTP.do_match/4
#         (ex_search) lib/ex_search/http.ex:3: ExSearch.HTTP.plug_builder_call/2
#         (plug) lib/plug/adapters/cowboy/handler.ex:15: Plug.Adapters.Cowboy.Handler.upgrade/4
#         (cowboy) src/cowboy_protocol.erl:442: :cowboy_protocol.execute/4

    redis |> sinter(Enum.map(words, fn(str) -> @token <> String.downcase(str) end))
    |> Enum.map(fn(id) -> { id, redis |> get(@content <> id) } end)
  end

end
