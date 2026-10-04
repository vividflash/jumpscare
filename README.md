# Jumpscare

A RuneLite plugin that rarely throws a full-screen jumpscare at you while you
play OSRS.

> **Photosensitivity / epilepsy warning:** the optional **Flash** mode rapidly
> flashes bright colours (red, white and black with the scary set, yellow,
> pink and blue with the happy one). Rapid flashing can trigger seizures in
> photosensitive individuals. If you are sensitive to flashing lights, leave
> flash disabled (the default) or disable the plugin entirely.

## Features

- **Test Mode**: the toggle at the top of the settings. Overrides the chance
  to 1 in 10 and fires one scare when you switch it on. Your chance returns
  when you switch it off, and it is off again after a client restart.

**General**

- **Chance (1 in N)**: rolled once per game tick (0.6 s). Default `6000`,
  about one scare per hour.
- **Duration**: how long the scare stays on screen, 1 ms to 10 s. Default
  `1000 ms`.

**Flash mode**

- **Enable flash (epilepsy warning)**: replaces the image with flashing
  colours (see the warning); enabling asks for confirmation. Default `off`.

**Appearance**

- **Image**: `Default` (creepy face), `Happy` (sun) or `Custom`. Custom falls
  back to Default if the file can't be loaded. Default `Default`.
- **Custom image file**: file name of an image inside your
  `.runelite/plugin-data/jumpscare` folder. Used when **Image** is `Custom`.
  Default blank.

**Sound**

- **Play sound**: Default `on`.
- **Sound**: `Default` (scream), `Happy` (jingle) or `Custom`. Custom falls
  back to Default if the file can't be loaded. Default `Default`.
- **Volume**: 0 to 100, independent of the in-game music and sound-effect
  sliders. At 0 nothing plays. Default `50`.
- **Custom sound file**: file name of a WAV inside your
  `.runelite/plugin-data/jumpscare` folder. Used when **Sound** is `Custom`.
  Default blank.

## Test command

`::stest` or `::jumpscaretest` fires a scare with your configured image and
sound, and prints a chat line showing which were used and why a custom file
could not be loaded.

- `::stest scary` (or `::stest s`): the bundled creepy face and scream.
- `::stest happy` (or `::stest h`): the bundled smiling sun and jingle.

## Custom files

Both go in your `.runelite/plugin-data/jumpscare` folder, created when the
plugin starts. They are re-checked on every scare, so a file added or
replaced later is picked up by the next one.

**Image**

- PNG, JPG, GIF or BMP (no WebP), scaled to fill the game canvas. Animated
  GIFs loop for the scare duration.
- Files over 4096 px on either side are refused and the default image is
  used.
- Static images are downscaled to at most 2048 px on their longest side.
- Animation frames are downscaled to at most 512 px on their longest side,
  and animations are cut to the first 30 frames.

**Sound**

- WAV (PCM) only. Convert other formats to WAV first.
