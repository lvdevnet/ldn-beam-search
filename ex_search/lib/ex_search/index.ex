defmodule ExSearch.Index do
  # please rewrite this
  # i want to cry

  defstruct idx: %{}

  def load_index do
    # load here
    %ExSearch.Index{}
    |> add_doc("Chrono_Trigger", ["game", "SNES", "1995"])
    |> add_doc("Legend_of_Mana", ["game", "PSX", "2000"])
    |> add_doc("Secret_of_Evermore", ["game", "SNES", "1996"])
  end

  defp add_doc(h, id, words) do
    key = String.to_atom(id)
    idx = Enum.reduce(words, h.idx, fn(word, idx) ->
      word_key = String.downcase(word)
      keys = Dict.get(idx, word_key, HashSet.new)
      Dict.put(idx, word_key, Set.put(keys, key))
    end)
    %{h | idx: idx}
  end

  def find_docs(h, words) do
    word_count = Enum.count(words)
    occurs = Enum.reduce(words, %{}, fn(word, occurs) ->
      word_key = String.downcase(word)
      keys = Dict.get(h.idx, word_key, HashSet.new)
      Enum.reduce(keys, occurs, fn(key, occurs) ->
        Dict.update(occurs, key, 1, &(&1 + 1))
      end)
    end)
    occurs
    |> Dict.to_list
    |> Enum.filter(fn({_, count}) -> count == word_count end)
    |> Enum.map(fn({key, _}) -> key end)
    |> Enum.into(HashSet.new)
  end

end
