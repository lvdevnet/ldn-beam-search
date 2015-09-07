defmodule ExSearch.IndexTest do
  use ExUnit.Case
  import ExSearch.Index

  test "doc search" do
    load_index()
    |> assert_find(["snes"], set([:Chrono_Trigger, :Secret_of_Evermore]))
    |> assert_find(["snes", "1995"], set([:Chrono_Trigger]))
  end

  defp assert_find(handle, words, expected_ids) do
    doc_ids = find_docs(handle, words)
    assert doc_ids == expected_ids
    handle
  end

  defp set(items) do
    Enum.into(items, HashSet.new)
  end
end
