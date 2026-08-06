# Jumpscare

A RuneLite plugin that rarely throws a full-screen jumpscare at you while you
play OSRS.

> **Photosensitivity / epilepsy warning:** the optional **Flash** mode rapidly
> flashes bright colours (red, white and black with the scary set, yellow, pink
> and blue with the happy one). Rapid flashing can trigger seizures in
> photosensitive individuals. If you are sensitive to flashing lights, leave
> flash disabled (the default) or disable the plugin entirely.

## What it does

Once per game tick, the plugin rolls a 1-in-N chance to trigger a jumpscare.
When it fires, it covers the whole game canvas with a scare image (or flashing
colours) for a configurable duration and plays a scream.

### The odds (default 1 in 6000)

The roll happens once per game tick (0.6 s), so the default of 6000 averages
out to one scare per hour of play. Lower the value to make it more
frequent, raise it to make it rarer.

## Testing it instantly

### Test Mode

**Test Mode** is the toggle at the top of the plugin settings. While it is on,
the chance is overridden to 1 in 10 and the sound plays at 80%, and switching
it on fires one scare straight away. It changes nothing in your saved
settings: your real chance, volume and **Play sound** take over again the
moment you switch it off, and it always resets to off when the client
restarts.

### The chat command

Type either of:

```
::stest
::jumpscaretest
```

They do the same thing. `::stest` is the short form; `::jumpscaretest` is
there in case another plugin claims the short one.

The command previews your configured **Image** and **Sound** sources. Two
bundled sets ship with the plugin, and you can force either one for a test
regardless of the settings:

- `::stest scary` (or `::stest s`) for the bundled creepy face and scream
- `::stest happy` (or `::stest h`) for the bundled smiling sun and jingle

This triggers a jumpscare immediately, regardless of the odds, so use it to
preview your settings and custom image or sound. The plain form also prints a
status line in chat showing which image and sound were used, including why a
custom file could not be loaded (not found, unsupported format, ...).

## Configuration

| Setting | Default | Notes |
| --- | --- | --- |
| Test Mode | off | Overrides chance to 1 in 10 and sound to 80%, fires one scare on enable, resets to off on restart. |
| Chance (1 in N) | 6000 | 1-in-N roll per game tick. See odds above. |
| Duration | 1000 ms | How long the scare stays on screen (max 10 s). |
| Enable flash | off | In the **Flash mode** section; replaces the image with flashing colours (see warning). |
| Image | Default | Default (creepy face) / Happy (sun) / Custom. Custom falls back to Default if the file can't be loaded. |
| Sound | Default | Default (scream) / Happy (jingle) / Custom. Custom falls back to Default if the file can't be loaded. |
| Play sound | on | Play the sound when the scare fires. |
| Volume | 80 | 0-100. See volume behaviour below. |
| Custom image file | (blank) | File name of an image inside your `.runelite/jumpscare` folder. Used when Image = Custom. |
| Custom sound file | (blank) | File name of a **WAV** inside your `.runelite/jumpscare` folder. Used when Sound = Custom. |

### Custom image

Drop an image into your `.runelite/jumpscare` folder (created when the
plugin starts), set **Image** to Custom and **Custom image file** to its file
name, e.g. `myscare.png`. It is scaled to fill the whole game canvas. If the
file can't be loaded, the plugin falls back to the default image. Image and
sound are picked independently, so your own image with the happy jingle is
fine.

Supported formats: **PNG, JPG, GIF, BMP** (no WebP). **Animated GIFs play**,
looping for the scare duration.

To keep memory bounded:

- Files over **4096 px** on either side are refused and the default image is
  used instead.
- Static images are downscaled to at most 2048 px on their longest side.
- Animation frames are downscaled to at most 512 px on their longest side, and
  long animations are truncated to the first 10 frames.

### Custom sound (WAV only)

Drop a **WAV** file into your `.runelite/jumpscare` folder, set **Sound** to
Custom and **Custom sound file** to its file name. Sound must be **WAV
(PCM)**, since the client has no MP3/MP4 codec. Convert other formats to WAV
first.

If the custom WAV can't be loaded, the plugin falls back to the bundled
scream.

Custom files are re-checked on every scare (and every `::stest`), so adding
a file late or replacing one under the same name is picked up by the next
trigger without a plugin toggle.

### Volume behaviour

The scream plays through the client's own audio subsystem, **independent of
the in-game music and sound-effect volume sliders**. Setting `Volume` to 0
disables playback entirely.

## Assets

All bundled assets (images, icon, sound) are original content made for this
plugin.

## License

BSD 2-Clause. See [LICENSE](LICENSE).

---

Co-A: Fable 5
