import { FC, useEffect, useState } from "react";

type SearchBoxProps = {
  placeholder: string;
  initial: string;
  onChange: (newValue: string) => void;
};

const SearchBox: FC<SearchBoxProps> = ({ placeholder, onChange, initial }) => {
  const [value, setValue] = useState(initial);

  return (
    <div style={{ paddingBottom: "20px" }}>
      <input
        type="text"
        value={value}
        placeholder={placeholder}
        onChange={(e) => {
          setValue(e.target.value);
          onChange(e.target.value);
        }}
        style={{ display: "block" }}
      ></input>
    </div>
  );
};

type SearchResultProps = { imageUrl: string; thumbnailUrl: string };
const SearchResult: FC<SearchResultProps> = ({ imageUrl, thumbnailUrl }) => {
  return (
    <>
      <a href={imageUrl} target="_blank" rel="noreferrer">
        <img src={thumbnailUrl} style={{ height: "200px" }}></img>
      </a>
    </>
  );
};

const App = () => {
  const [query, setQuery] = useState("doggos");

  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState<readonly { url: string }[]>([]);

  useEffect(() => {
    setSearching(true);
    const tim = setTimeout(() => {
      setResults(
        query.split("").map((_, i) => ({
          url: "https://placedog.net/" + (200 + i * 10),
        }))
      );
      setSearching(false);
    }, 500);

    () => {
      clearTimeout(tim);
      setSearching(false);
    };
  }, [query]);

  const resultViews = results.map(({ url }, i) => {
    return <SearchResult imageUrl={url} thumbnailUrl={url} key={i} />;
  });

  const ellipsis = searching ? "searching..." : undefined;

  return (
    <>
      <h1 style={{ fontFamily: "Helvetica" }}>Search my doggos</h1>
      <SearchBox
        placeholder="Search phrase"
        initial={query}
        onChange={(newQuery) => setQuery(newQuery)}
      />
      {resultViews}
      {ellipsis}
    </>
  );
};

export default App;
