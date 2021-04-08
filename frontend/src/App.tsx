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

type SearchResultProps = { offset: number };
const SearchResult: FC<SearchResultProps> = ({ offset }) => {
  const url = "https://placedog.net/" + (200 + offset * 10);
  return (
    <>
      <a href={url} target="_blank" rel="noreferrer">
        <img src={url} style={{ height: "200px" }}></img>
      </a>
    </>
  );
};

const App = () => {
  const [query, setQuery] = useState("doggos");

  const results = query
    .split("")
    .map((_, i) => <SearchResult offset={i} key={i} />);

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
