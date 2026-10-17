import { Badge } from "@mantine/core";

interface CountBadgeProps {
  count: number;
  label: string;
}

export function CountBadge({ count, label }: CountBadgeProps) {
  if (count < 1) return null;

  return (
    <Badge size="sm" variant="light" aria-label={`${count} ${label}`}>
      {count}
    </Badge>
  );
}
