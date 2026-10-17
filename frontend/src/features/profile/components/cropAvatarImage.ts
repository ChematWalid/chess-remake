import type { Area } from "react-easy-crop";

export async function cropAvatarImage(
  imageUrl: string,
  area: Area,
): Promise<File> {
  const image = new Image();
  image.src = imageUrl;
  await image.decode();

  const canvas = document.createElement("canvas");
  const resolution = 512;
  canvas.width = resolution;
  canvas.height = resolution;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Your browser could not prepare this image.");

  context.drawImage(
    image,
    area.x,
    area.y,
    area.width,
    area.height,
    0,
    0,
    resolution,
    resolution,
  );

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (result) =>
        result
          ? resolve(result)
          : reject(new Error("Could not prepare this image.")),
      "image/jpeg",
      0.92,
    );
  });

  return new File([blob], "profile-avatar.jpg", { type: "image/jpeg" });
}
