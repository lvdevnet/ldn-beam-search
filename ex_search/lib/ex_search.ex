import Logger

defmodule ExSearch do
  use Plug.Router

  plug :match
  plug :dispatch

  def main do
    Plug.Adapters.Cowboy.http(ExSearch, [])
  end

  def init(opts) do
    opts
  end

  get "/" do
    info("GET /", [query_string: conn.query_string])

    conn
    |> put_resp_content_type("text/plain")
    |> send_resp(200, "Hello world")
  end

  match _ do
    send_resp(conn, 404, "not found\n")
  end
end
