package com.alienworks.transferplaylist;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.farng.mp3.MP3File;
import org.farng.mp3.TagException;
import org.farng.mp3.id3.ID3v1;

/**
 *
 * @author Tickler
 */
public class Common {
    //Create a common logger
    private static final Logger logger = Logger.getLogger(Common.class.getName());
    {
        logger.setLevel(Level.ALL);
    }
    
    static class Song {
        /**
           Represents track objects that will be displayed in the interface
        */
        private final String _title;
        public Song(String songTitle) {
            /**
             * @param songTitle the title of the song
             */
            _title = songTitle;
        }
        
        public String title() { return _title;}
        
        @Override
        public boolean equals(Object anotherSong) {
            if (anotherSong instanceof Song){
                return _title.equals( ((Song)anotherSong).title() );
            } else {
                return false;
            }
        }
        
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + Objects.hashCode(this._title);
            return hash;
        }
    }
    
    /**
     * A list of songs.
     * Iteration over an Album returns songs in this collection.
     */
    static class Album extends ArrayList<Song> {
        private final String _title;
        
        public Album(String title) {
            super();
            _title = title;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this._title);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            /**
             * An album equals another when they have the same title, regardless
             * of case.
             */
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Album other = (Album) obj;
            if (!Objects.equals(this._title.toLowerCase(), 
                    other._title.toLowerCase())) {
                return false;
            }
            return true;
        }
        
        @Override
        public boolean add(Song toAdd) {
            //Add a song only if the album doesn't already have it
            if (!contains(toAdd)) {
                return super.add(toAdd);
            }
            return false;
        }
        
    }
    
    static class Artist implements Iterable<Album> {
        
        private final ArrayList<Album> _albums;
        private final String _name;
        public Artist(String name) {
            _albums = new ArrayList<>();
            _name = name;
        }
        
        public ArrayList<Album> albums() { return _albums;}
        
        public String name() {return _name;}
        
        public void addAlbum(Album toAdd) {
            if (!hasAlbum(toAdd)) {
                _albums.add(toAdd);
            }
        }
        
        private boolean hasAlbum(Album album) {
            return _albums.contains(album);
        }
        
        @Override
        public Iterator<Album> iterator() {
            //Iteration over artists should give albums
            return _albums.iterator();
        }
    }
    
    static class TrackInfo {
        /*
         Represents raw tag information for a music file as read from disk
        */
        private final String _artist;
        private final String _album;
        private final String _title;
        private final String _release_date;
        
        private TrackInfo(String title, String artist, String album, String releaseDate) {
            _title= title;
            _artist = artist;
            _album = album;
            _release_date = releaseDate;
        }
        
        public String artist() { return _artist; }
        public String title() { return _title; }
        public String album() { return _album; }
        public String releaseDate() {return _release_date;}
        
        public static TrackInfo fromFile(File inFile) {
            //Returns a TrackInfo object or otherwise null on failure
            try {
                MP3File mp3file = new MP3File(inFile);
                ID3v1 id3v1 = mp3file.getID3v1Tag();
                return new TrackInfo(id3v1.getTitle(), id3v1.getArtist(), id3v1.getAlbum(),
                        id3v1.getYearReleased());
            } catch (    TagException | IOException tex) {
                logger.warning(tex.getMessage());
            }
            return null;
        }
    }
    
    interface IPlaylist {
        /*
         * There should be the following:
         * public static IPlaylist fromFile(File playlist);
         * But Java doesn't allow static methods in interfaces. It will be in Java 8
        */
        void save();
        void popFile(String fname);
    }
    
    static class M3UPlaylist implements IPlaylist, Iterable<Artist> {
        private final File playlist_file;
        private final ArrayList<String> track_file_names = 
                new ArrayList<>();
        private List<Artist> artists;
        private final PropertyChangeSupport progress_pcs = 
                new PropertyChangeSupport(this);
        
        public M3UPlaylist(File playlistFile) {
            playlist_file = playlistFile;
        }
        
        public void scanPlaylist() throws IOException{
            //Get the filename to create path and scanner
            String pls_file_name = getFileName(playlist_file);
            Path pls_file_path = Paths.get(pls_file_name);
            ArrayList<TrackInfo> track_infos = new ArrayList<>();
            try (Scanner pls_reader = new Scanner(pls_file_path, "UTF-8")) {
                while(pls_reader.hasNextLine()) { //collect file names
                    String line = pls_reader.nextLine();
                    if (line.startsWith("#")){ //This is a comment line. Ignore
                        continue;
                    }
                    track_file_names.add(line);
                }
                //Read the tag data from file name (line)
                for (int i=0; i < track_file_names.size(); ++i){
                    progress_pcs.firePropertyChange("progress", i-1 , i);
                    File track_file = new File(track_file_names.get(i));
                    if (!track_file.exists()) {
                        //TODO: I think it's better to just log it
                        throw new FileNotFoundException(track_file.getPath());
                    } else {
                        track_infos.add(TrackInfo.fromFile(track_file));
                    }
                }
                artists = getArtistList(track_infos);
            }
        }
        
        private static String getFileName(File f){
            if (f.isFile()) {
                return f.getPath();
            }
            return "";
        }
        
        private static List<Artist> getArtistList(List<TrackInfo> trackInfos) {
            List<Song> all_songs = new ArrayList<>();
            List<Album> all_albums = new ArrayList<>();
            List<Artist> result = new ArrayList<>();
            for (TrackInfo ti : trackInfos) {
                //create a song from the track info
                Song song = new Song(ti.title());
                if (!all_songs.contains(song)) {
                    all_songs.add(song);
                    Album album = new Album(ti.album());
                    if (!all_albums.contains(album)) {
                        all_albums.add(album);
                        Artist artist = new Artist(ti.artist());
                        if (!result.contains(artist)) {
                            result.add(artist);
                        }
                    }
                }
            }
            return result;
        }
        
        @Override
        public void save() {
            
        }
        
        @Override
        public void popFile(String fname) {
            track_file_names.remove(fname);
        }
        
        @Override
        public Iterator<Artist> iterator() {
            //Iteration over the playlist should return artists
            return artists.iterator();
        }
        
        public void addPropertChangeListener(PropertyChangeListener pcl){
            progress_pcs.addPropertyChangeListener(pcl);
        }
        
        public void removePropertChangeListener(PropertyChangeListener pcl) {
            progress_pcs.removePropertyChangeListener(pcl);
        }
    }
}
