# Jumpscare

A RuneLite plugin that, very rarely, throws a full-screen jumpscare (a creepy
image plus a scream sound) at you while you play Old School RuneScape. You have
been warned.

> **Photosensitivity / epilepsy warning:** the optional **Flash** mode rapidly
> flashes red, white and black. Rapid flashing can trigger seizures in
> photosensitive individuals. If you are sensitive to flashing lights, leave
> flash disabled (the default) or disable the plugin entirely.

## What it does

Once per game tick, the plugin rolls a 1-in-N chance to trigger a jumpscare.
When it fires, it covers the whole game canvas with a scare image (or flashing
colours) for a configurable duration and plays a scream.

### The odds (default 1 in 10000)

The roll happens once per game tick (0.6 s), so the default of 10000 averages
out to roughly one scare per ~1.7 hours of logged-in play. Lower the value to
make it more frequent, raise it to make it rarer.

## Testing it instantly

Type the chat command:

```
::stest
```

`::stest` uses your configured **Theme**. Two bundled themes ship with the
plugin, and you can force either one for a test regardless of the setting:

- `::stest scary` (or `::stest s`) — creepy face + scream (respects your
  custom image/sound files if set)
- `::stest happy` (or `::stest h`) — smiling sun + cheerful jingle, for
  testing without the heart attack

This triggers a jumpscare immediately, regardless of the odds, so you can see
what it looks like and tune your settings. It works even outside of combat and
is the intended way to preview your custom image/sound.

## Configuration

| Setting | Default | Notes |
| --- | --- | --- |
| Chance (1 in N) | 10000 | 1-in-N roll per game tick. See odds above. |
| Duration (ms) | 1000 | How long the scare stays on screen. |
| Enable flash | off | In the **Flash mode** section; replaces the image with flashing colours (see warning). |
| Play sound | on | Play the scream when the scare fires. |
| Volume | 80 | 0-100. See volume behaviour below. |
| Custom image file | (blank) | File name of an image inside your `.runelite/jumpscare` folder. Blank = bundled image. |
| Custom sound file | (blank) | File name of a **WAV** inside your `.runelite/jumpscare` folder. Blank = bundled scream. |

### Custom image

Drop an image into your `.runelite/jumpscare` folder (created when the
plugin starts) and set **Custom image file** to its file name, e.g.
`myscare.png`. It is scaled to fill the whole game canvas. If the file can't
be loaded, the plugin falls back to the bundled image and logs a warning.

Supported formats are what Java decodes out of the box: **PNG, JPG, GIF,
BMP** (no WebP — that would need a third-party codec). **Animated GIFs
play**, looping for the scare duration. To keep memory bounded, animation
frames are downscaled to at most 640 px on their longest side and very long
animations are truncated (at most 150 frames / 64 MB decoded).

### Custom sound (WAV only)

Drop a **WAV** file into your `.runelite/jumpscare` folder and set
**Custom sound file** to its file name. The client
plays audio through `javax.sound.sampled` (via RuneLite's `AudioPlayer`), which
only supports **WAV / PCM** out of the box — there is **no MP3 or MP4 codec
support**. If you want a specific jumpscare clip (for example one from a horror
game), you must convert it to WAV yourself first; we do **not** bundle any
copyrighted audio or images. Anything you supply is your own responsibility.

If the custom WAV can't be loaded, the plugin falls back to the bundled scream
and logs a warning.

### Volume behaviour

The scream plays through the client's own audio subsystem
(`javax.sound.sampled` / RuneLite `AudioPlayer`), **independent of the in-game
RuneScape music and sound-effect volume sliders**. It always plays at the
plugin's own `Volume` config level regardless of those in-game sliders. Setting
`Volume` to 0 disables playback entirely. Muting your whole application or
system audio output will, of course, still silence it.

## Assets

The bundled scare image, icon and scream sound are all original, procedurally
generated content created for this plugin. No copyrighted characters, images or
audio are included.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
