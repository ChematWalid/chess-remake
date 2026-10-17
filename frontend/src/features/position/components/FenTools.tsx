import { Button, Group, Textarea } from "@mantine/core";

interface FenToolsProps {
  value: string;
  onChange: (value: string) => void;
  onImport: () => void;
}

export function FenTools({ value, onChange, onImport }: FenToolsProps) {
  return (
    <>
      <Textarea
        label="FEN import or export"
        autosize
        minRows={2}
        value={value}
        onChange={(event) => onChange(event.currentTarget.value)}
      />
      <Group>
        <Button variant="default" onClick={onImport}>
          Import FEN
        </Button>
        <Button
          variant="default"
          onClick={() => void navigator.clipboard.writeText(value)}
        >
          Copy FEN
        </Button>
      </Group>
    </>
  );
}
