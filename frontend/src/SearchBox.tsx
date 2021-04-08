import { FC, useState } from "react";

type SearchBoxProps = {
  placeholder: string;
  initial: string;
  onChange: (newValue: string) => void;
};

export const SearchBox: FC<SearchBoxProps> = ({
  placeholder,
  onChange,
  initial,
}) => {
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
