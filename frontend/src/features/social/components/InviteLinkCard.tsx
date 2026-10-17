import { Button, Group, Paper, Text, TextInput } from "@mantine/core";
import { IconCopy, IconCheck } from "@tabler/icons-react";
import { useRef, useState } from "react";
import { notifications } from "@mantine/notifications";

export function InviteLinkCard({ url }: { url: string }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [copied, setCopied] = useState(false);

  async function copyLink() {
    try {
      if (!navigator.clipboard?.writeText)
        throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(url);
      setCopied(true);
      notifications.show({
        title: "Invite copied",
        message: "Share the one-time link with a friend.",
        color: "green",
      });
    } catch {
      inputRef.current?.focus();
      inputRef.current?.select();
      notifications.show({
        title: "Invite link ready",
        message:
          "Clipboard access is unavailable. The link is selected so you can copy it.",
        color: "blue",
      });
    }
  }

  return (
    <Paper className="panel" p="md" withBorder radius="md">
      <Group align="end" wrap="wrap">
        <TextInput
          ref={inputRef}
          label="Your invitation link"
          description="Share this one-time link with the person you want to add."
          value={url}
          readOnly
          style={{ flex: 1, minWidth: 260 }}
          onFocus={(event) => event.currentTarget.select()}
        />
        <Button
          leftSection={
            copied ? <IconCheck size={16} /> : <IconCopy size={16} />
          }
          onClick={() => void copyLink()}
        >
          {copied ? "Copied" : "Copy link"}
        </Button>
      </Group>
      <Text size="xs" c="dimmed" mt="xs">
        Creating another link will replace this one.
      </Text>
    </Paper>
  );
}
