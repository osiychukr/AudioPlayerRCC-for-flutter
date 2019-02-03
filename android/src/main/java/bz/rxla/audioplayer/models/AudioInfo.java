package bz.rxla.audioplayer.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class AudioInfo implements Parcelable {
    public String name;
    public String title;
    public String imageUrl;
    public int duration;
    public Bitmap bitmap;

    public AudioInfo(String name, String title, String imageUrl, int duration) {
        this.name = name;
        this.title = title;
        this.imageUrl = imageUrl;
        this.duration = duration;
    }

    protected AudioInfo(Parcel in) {
        name = in.readString();
        title = in.readString();
        imageUrl = in.readString();
        duration = in.readInt();
        bitmap = in.readParcelable(Bitmap.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(title);
        dest.writeString(imageUrl);
        dest.writeInt(duration);
        dest.writeParcelable(bitmap, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AudioInfo> CREATOR = new Creator<AudioInfo>() {
        @Override
        public AudioInfo createFromParcel(Parcel in) {
            return new AudioInfo(in);
        }

        @Override
        public AudioInfo[] newArray(int size) {
            return new AudioInfo[size];
        }
    };
}
