import { Group, Switch, Text } from "@mantine/core";

interface SettingToggleProps {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

export function SettingToggle({
  label,
  description,
  checked,
  onChange,
}: SettingToggleProps) {
  return (
    <Group
      className="settings-option"
      justify="space-between"
      wrap="nowrap"
      gap="lg"
    >
      <div className="settings-option__copy">
        <Text fw={600} size="sm">
          {label}
        </Text>
        <Text size="sm" c="dimmed">
          {description}
        </Text>
      </div>
      <Switch
        checked={checked}
        onChange={(event) => onChange(event.currentTarget.checked)}
        aria-label={label}
      />
    </Group>
  );
}
