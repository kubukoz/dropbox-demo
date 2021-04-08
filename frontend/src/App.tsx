import { FC, useState } from "react";

type SearchBoxProps = {
  placeholder?: string;
  onChange?: (newValue: string) => void;
};

const SearchBox: FC<SearchBoxProps> = ({ placeholder, onChange }) => {
  const [value, setValue] = useState("");

  return (
    <input
      type="text"
      value={value}
      placeholder={placeholder}
      onChange={(e) => {
        setValue(e.target.value);
        onChange && onChange(e.target.value);
      }}
    ></input>
  );
};

const App = () => {
  return (
    <>
      <SearchBox
        placeholder="Search phrase"
        onChange={(newQuery) => console.log(newQuery)}
      />
    </>
  );
};

export default App;
