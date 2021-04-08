import { FC, useState } from "react";

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

  const results = query.split("").map((_, i) => {
    const url = "https://placedog.net/" + (200 + i * 10);

    return <SearchResult imageUrl={url} thumbnailUrl={url} key={i} />;
  });

  return (
    <>
      <h1 style={{ fontFamily: "Helvetica" }}>Search my doggos</h1>
      <SearchBox
        placeholder="Search phrase"
        initial={query}
        onChange={(newQuery) => setQuery(newQuery)}
      />
      {results}
    </>
  );
};

export default App;
