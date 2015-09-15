import Logger

defmodule ExSearch.HTTP do
  use Plug.Router
  import Plug.Conn.Query
 #import ExSearch.Index

  plug :match
  plug :dispatch

  def init(opts) do
   #Agent.start_link(fn -> load_index() end, name: __MODULE__)
    info("Running")
    opts
  end

  get "/content" do
    q = decode(conn.query_string)["q"] |> String.split(" ", trim: true)
   #info("GET /", [query: Enum.join(q, "+")])

   #res = Agent.get(__MODULE__, fn(index) -> find_docs(index, q) end)
   #|> Enum.join("\n")
    res = ExSearch.Redis.find_docs(q) |> Enum.map_join("\n", &("#{elem(&1, 0)},#{elem(&1, 1)}"))
    conn
    |> put_resp_content_type("text/plain")
    |> put_resp_header("server", "ex_search/plug/cowboy")
    |> send_resp(200, res)
  end

  match _ do
    send_resp(conn, 404, "not found\n")
  end
end

