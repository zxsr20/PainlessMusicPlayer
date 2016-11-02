/*
 * Copyright (C) 2016 Yaroslav Mytkalyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.doctoror.fuckoffmusicplayer.playlist;

import com.doctoror.fuckoffmusicplayer.nowplaying.NowPlayingActivity;
import com.doctoror.fuckoffmusicplayer.playback.PlaybackService;
import com.doctoror.fuckoffmusicplayer.util.SelectionUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Yaroslav Mytkalyk on 19.10.16.
 */

public final class PlaylistUtils {

    private PlaylistUtils() {

    }

    public static void play(@NonNull final Context context,
            @NonNull final List<Media> mediaList,
            @NonNull final Media media,
            final int position) {
        final Playlist playlist = Playlist.getInstance(context);
        playlist.setPlaylist(mediaList);
        playlist.setMedia(media);
        playlist.setIndex(position);
        playlist.setPosition(0);
        playlist.persistAsync();

        PlaybackService.play(context);
        context.startActivity(new Intent(context, NowPlayingActivity.class));
    }

    @Nullable
    @WorkerThread
    public static List<Media> fromAlbum(@NonNull final ContentResolver resolver,
            final long albumId,
            @NonNull final String albumArt) {
        return fromAlbums(resolver, new long[]{albumId}, new String[]{albumArt}, null);
    }

    @Nullable
    @WorkerThread
    public static List<Media> fromAlbums(@NonNull final ContentResolver resolver,
            @NonNull final long[] albumIds,
            @NonNull final String[] albumArts,
            @Nullable final Long forArtist) {
        if (albumIds.length != albumArts.length) {
            throw new IllegalArgumentException("ids lengths does not match arts length");
        }
        final List<Media> playlist = new ArrayList<>(15 * albumIds.length);
        for (int i = 0; i < albumIds.length; i++) {
            final long albumId = albumIds[i];
            final StringBuilder selection = new StringBuilder(256);
            selection.append(MediaStore.Audio.Media.ALBUM_ID).append('=').append(albumId);
            if (forArtist != null) {
                selection.append(" AND ")
                        .append(MediaStore.Audio.Media.ARTIST_ID).append('=').append(forArtist);
            }
            final Cursor c = resolver.query(Query.CONTENT_URI,
                    Query.PROJECTION,
                    selection.toString(),
                    null,
                    MediaStore.Audio.Media.TRACK);
            if (c != null) {
                final String albumArt = albumArts[i];
                for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                    playlist.add(mediaFromCursor(c, albumArt));
                }
                c.close();
            }
        }
        return playlist;
    }

    @Nullable
    public static List<Media> forTracks(@NonNull final ContentResolver resolver,
            @NonNull final long[] trackIds,
            @Nullable final String sortOrder) {
        final List<Media> playlist = new ArrayList<>(trackIds.length);
        final Cursor c = resolver.query(Query.CONTENT_URI,
                Query.PROJECTION,
                SelectionUtils.inSelectionLong(MediaStore.Audio.Media._ID, trackIds),
                null,
                sortOrder);
        if (c != null) {
            for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                playlist.add(mediaFromCursor(c, null));
            }
            c.close();
        }
        return playlist;
    }

    @NonNull
    private static Media mediaFromCursor(@NonNull final Cursor c,
            @Nullable final String albumArt) {
        final Media media = new Media();
        media.id = c.getLong(0);
        media.track = c.getInt(1);
        media.title = c.getString(2);
        media.artist = c.getString(3);
        media.album = c.getString(4);
        media.albumArt = albumArt;
        media.duration = c.getLong(5);
        final String path = c.getString(6);
        if (!TextUtils.isEmpty(path)) {
            media.data = Uri.parse(new File(path).toURI().toString());
        }
        return media;
    }

    private static final class Query {

        static final Uri CONTENT_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        static final String[] PROJECTION = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
        };
    }
}
