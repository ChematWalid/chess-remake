let audioContext: AudioContext | null = null;
let audioUnlocked = false;

export function unlockUiAudio(): void {
  try {
    audioContext ??= new AudioContext();
    if (audioContext.state === "suspended") void audioContext.resume();
    audioUnlocked = true;
  } catch {}
}

/**
 * Plays a best-effort UI tone without interrupting gameplay if audio is unavailable.
 */
export function playUiTone(frequency = 660): void {
  try {
    if (!audioUnlocked || !audioContext) return;
    if (audioContext.state === "suspended") void audioContext.resume();
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();
    oscillator.type = "sine";
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(0.0001, audioContext.currentTime);
    gain.gain.exponentialRampToValueAtTime(
      0.075,
      audioContext.currentTime + 0.015,
    );
    gain.gain.exponentialRampToValueAtTime(
      0.0001,
      audioContext.currentTime + 0.12,
    );
    oscillator.connect(gain);
    gain.connect(audioContext.destination);
    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.13);
  } catch {}
}
