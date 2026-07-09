# Jumpscare

A RuneLite plugin that, very rarely, throws a full-screen jumpscare (a creepy
image plus a scream sound) at you while you play Old School RuneScape. You have
been warned.

> **Photosensitivity / epilepsy warning:** the optional **Flash** mode rapidly
> flashes red, white and black. Rapid flashing can trigger seizures in
> photosensitive individuals. If you are sensitive to flashing lights, use
> **Image** mode (the default) or disable the plugin entirely.

## What it does

Once per game tick, the plugin rolls a 1-in-N chance to trigger a jumpscare.
When it fires, it covers the whole game canvas with a scare image (or flashing
colours) for a configurable duration and plays a scream.

### The odds (default 1 in 100000)

The roll happens **once per game tick**, and a tick is **0.6 seconds**. So the
expected number of ticks between scares at the default `chanceDenominator` of
100000 is 100000 ticks, which is:

```
100000 ticks x 0.6 s/tick = 60000 s = 60000 / 3600 = ~16.7 hours
```

of continuous logged-in play, on average, between scares. Lower the value to
make it more frequent (e.g. 1000 for testing), raise it to make it rarer.

## Testing it instantly

Type the chat command:

```
::stest
```

(Not `::jumpscare` — that is a real built-in game command that makes your
character jump, added by a Halloween event, so this plugin uses a different
name to avoid the collision.)

`::stest` uses your configured **Theme**. Two bundled themes ship with the
plugin, and you can force either one for a test regardless of the setting:

- `::stest scary` (or `::stest s`) — creepy face + scream (respects your
  custom image/sound paths if set)
- `::stest happy` (or `::stest h`) — smiling sun + cheerful jingle, for
  testing without the heart attack

This triggers a jumpscare immediately, regardless of the odds, so you can see
what it looks like and tune your settings. It works even outside of combat and
is the intended way to preview your custom image/sound.

## Configuration

| Setting | Default | Notes |
| --- | --- | --- |
| Chance (1 in N) | 100000 | 1-in-N roll per game tick. See odds math above. |
| Duration (ms) | 1000 | How long the scare stays on screen. |
| Mode | Image | `Image` = full-screen picture. `Flash` = flashing colours (see warning). |
| Play sound | on | Play the scream when the scare fires. |
| Volume | 80 | 0-100. See volume behaviour below. |
| Custom image path | (blank) | Absolute path to a PNG/JPG. Blank = bundled image. |
| Custom sound path | (blank) | Absolute path to a **WAV** file. Blank = bundled scream. |

### Custom image

Set **Custom image path** to the absolute path of a PNG or JPG file, e.g.
`C:\Users\me\Pictures\myscare.png`. It is scaled to fill the whole game canvas.
If the file can't be loaded, the plugin falls back to the bundled image and
logs a warning.

### Custom sound (WAV only)

Set **Custom sound path** to the absolute path of a **WAV** file. The client
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
