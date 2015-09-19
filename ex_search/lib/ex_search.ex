defmodule ExSearch do
  def main do
    {:ok, _} = ExSearch.Redis.start_link
    Plug.Adapters.Cowboy.http(ExSearch.HTTP, [])
  end
end
