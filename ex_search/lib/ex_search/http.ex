import Logger

defmodule ExSearch.HTTP do
  use Plug.Router
  import Plug.Conn.Query
  import ExSearch.Index

  plug :match
  plug :dispatch

  def init(opts) do
    Agent.start_link(fn -> load_index() end, name: __MODULE__)
    info("Running")
    opts
  end

  get "/content" do
    q = decode(conn.query_string)["q"] |> String.split(" ", trim: true)
    info("GET /", [query: Enum.join(q, "+")])

    res = Agent.get(__MODULE__, fn(index) -> find_docs(index, q) end)
    |> Enum.join("\n")
    conn
    |> put_resp_content_type("text/plain")
    |> send_resp(200, res)
  end

  match _ do
    send_resp(conn, 404, "not found\n")
  end
end

