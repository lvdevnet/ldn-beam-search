defmodule ExSearch do
  def main do
    Plug.Adapters.Cowboy.http(ExSearch.HTTP, [])
  end
end
