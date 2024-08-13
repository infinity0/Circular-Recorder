# Circular Recorder

Record continuously in the background, deleting old audio files so your storage
doesn't fill up too much. Also known as "time travel recording".

Audio is stored on external storage not RAM, so the length of time which you
can "travel back" can be quite long, depending on what settings you configure.

## Installation

You can install this as a normal non-system app alongside the regular LineageOS
Sound Recorder which is a system app.

## Where are my files

Circular recordings are stored in the `Recordings` folder of external storage,
inside a subfolder named after the initial period, e.g. `Recordings/Sound
record ($initial_date $initial_time)/Sound record ($date $time).m4a`.

## Settings

There are two settings - `$period` and `$number`. For each circular recording
(i.e. manual press of the "start" button), we record separate audio files each
lasting for `$period`, and retain the newest `$number` of files.

By default, `$period` = 1 hour and `$number` = 3.

When you press "stop", the current period is also retained, so in practise
you'll end up with `$number + 1` files.

## Technical details

We require battery optimisations to be switched off, as our technique requires
automatically restarting the audio recording service from a background service.
On Android, this is only allowed for apps that have battery optimisations off.
