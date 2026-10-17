import { Alert, Center, Loader, Stack, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";

export function LoadingState({ label = "Loading" }: { label?: string }) {
  return (
    <Center mih={240}>
      <Stack align="center" gap="sm">
        <Loader color="green" size="sm" />
        <Text c="dimmed" size="sm">
          {label}
        </Text>
      </Stack>
    </Center>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <Alert
      color="red"
      icon={<IconAlertCircle size={18} />}
      title="Something went wrong"
      withCloseButton={false}
    >
      {message}
      {onRetry ? (
        <button className="text-button" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </Alert>
  );
}

export function EmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="empty-state">
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}
