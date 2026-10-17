import { useEffect, useState } from "react";
import Cropper, { type Area } from "react-easy-crop";
import {
  Avatar,
  Button,
  FileButton,
  Group,
  Modal,
  Slider,
  Stack,
  Text,
  UnstyledButton,
} from "@mantine/core";
import { IconCamera } from "@tabler/icons-react";
import { cropAvatarImage } from "./cropAvatarImage";
import "./AvatarEditor.css";

interface AvatarEditorProps {
  avatarUrl: string | null;
  displayName: string;
  isSaving: boolean;
  onSave: (file: File) => void;
}

export function AvatarEditor({
  avatarUrl,
  displayName,
  isSaving,
  onSave,
}: AvatarEditorProps) {
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedArea, setCroppedArea] = useState<Area | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(
    () => () => {
      if (imageUrl) URL.revokeObjectURL(imageUrl);
    },
    [imageUrl],
  );

  function selectImage(file: File | null) {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      setError("Choose an image smaller than 5 MB.");
      return;
    }
    setError(null);
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setImageUrl(URL.createObjectURL(file));
  }

  async function saveCrop() {
    if (!imageUrl || !croppedArea) return;
    try {
      setError(null);
      const image = await cropAvatarImage(imageUrl, croppedArea);
      onSave(image);
      setImageUrl(null);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Could not prepare this image.",
      );
    }
  }

  return (
    <>
      <Stack gap="xs" align="flex-start">
        <FileButton
          onChange={selectImage}
          accept="image/png,image/jpeg,image/webp"
        >
          {(props) => (
            <UnstyledButton
              {...props}
              className="avatar-editor__trigger"
              aria-label="Change profile photo"
              title="Change profile photo"
            >
              <Avatar src={avatarUrl} size={76} radius="xl">
                {displayName.slice(0, 1)}
              </Avatar>
              <span className="avatar-editor__camera">
                <IconCamera size={17} />
              </span>
            </UnstyledButton>
          )}
        </FileButton>
        {error && !imageUrl && (
          <Text size="xs" c="red">
            {error}
          </Text>
        )}
      </Stack>

      <Modal
        opened={imageUrl !== null}
        onClose={() => setImageUrl(null)}
        title="Adjust your profile photo"
        centered
        size="md"
      >
        <Stack>
          <Text size="sm" c="dimmed">
            Drag to position your photo. The circular preview shows how it will
            appear.
          </Text>
          <div className="avatar-editor__crop-stage">
            {imageUrl && (
              <Cropper
                image={imageUrl}
                crop={crop}
                zoom={zoom}
                aspect={1}
                cropShape="round"
                showGrid={false}
                onCropChange={setCrop}
                onZoomChange={setZoom}
                onCropComplete={(_, pixels) => setCroppedArea(pixels)}
              />
            )}
          </div>
          <label className="avatar-editor__zoom-label" htmlFor="avatar-zoom">
            Zoom
          </label>
          <Slider
            id="avatar-zoom"
            min={1}
            max={3}
            step={0.01}
            value={zoom}
            onChange={setZoom}
            aria-label="Zoom profile photo"
          />
          {error && (
            <Text size="sm" c="red" role="alert">
              {error}
            </Text>
          )}
          <Group justify="flex-end" mt="sm">
            <Button variant="default" onClick={() => setImageUrl(null)}>
              Cancel
            </Button>
            <Button onClick={() => void saveCrop()} loading={isSaving}>
              Save photo
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
