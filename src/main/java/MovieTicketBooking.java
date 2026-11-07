import java.awt.desktop.ScreenSleepEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

//"Design a movie ticket booking system similar to BookMyShow that allows users to browse movies, "+
//        "select theaters and showtimes, book tickets, and manage reservations
//
//
//Requirement:
//        1.
//browse movie;
//you can
//        search movie, see
//where is
//playing
//you can
//select theater
//to check for
//show time
//Simple text
//matching on
//the movie
//title;
//2.
//        select theaters, showtimes, select
//seats;
//expose seat
//availability
//3.
//book tickets:
//can cancel
//and rebook
//only
//seat pricing
//is idenitical;
//handle concurrency
//booking;
//flow:
//        1.
//browse showtime;
//a.flow A:
//display movie;
//which theatre
//is playing;
//        theatre shows, movie, screens
//and playing
//time;
//b.flow B:
//display theatre
//and same
//as flow
//A
//
//2.
//ticket booking;
//
//
//Entity:
//        1.ReservationSystem
//2.Theater;
//3.ShowTime;
//4.Movie;
//5.Screen;
//6.Seat;
//7.Ticket;
//
//Entity Design:
//        1.ReservationSystem:
//        -List<Theatre>;
//
//List<Movie> listMovie();
//
//List<Theatre> listTheatre();
//
//List<ShowTime> listShowTime(Movie);
//
//List<ShowTime> listShowTime(Theatre);
//
////booking
//Reservation reserveSeat(Showtime, String seatID);
////cancel booking
//
//
//2.Theatre:
//        -ShowTime;
//    -name
//3.ShowTime:
//        -movie;
//    -theatre;
//    -screenId;
//    -startTime;
//    -List<Reservation>
//
//List<String> availableSeat();
//
//boolean takeSeat(String seatId)
//
//4.Reservation,pickshowtime ->
//show occupancy ->
//pick seat ->
//generate Ticket;
//    -
//String id
//    -SeatID
//    -movieTime
//    -startTime
//    -TheaterName


public class MovieTicketBooking {
}

class BookingSystem{
        private final List<Theatre> theatres;

        public BookingSystem(List<Theatre> theatres) {
            this.theatres = theatres;
        }

        public List<ShowTime> listShowTime(Movie movie){
            if (movie == null){
                throw new IllegalArgumentException("movie cannot be null");
            }
            List<ShowTime> res = new ArrayList<>();
            for(Theatre theatre : theatres){
                List<ShowTime> showTimesByTheatre = theatre.getShowTimeByMovie(movie);
                LocalDateTime now = LocalDateTime.now();
                for(ShowTime showTime: showTimesByTheatre){
                    if (showTime.getStartTime() .isAfter(LocalDateTime.now())){
                        res.add(showTime);
                    }
                }
            }
            return res;

        }

        public List<ShowTime> listShowTime(Theatre theatre){
            if (theatre == null){
                throw new IllegalArgumentException("theatre cannot be null");
            }
            List<ShowTime> res = new ArrayList<>();
            for(ShowTime showTime: theatre.getShowTimes()){
                LocalDateTime now = LocalDateTime.now();
                if(showTime.getStartTime() .isAfter(LocalDateTime.now())){
                    res.add(showTime);
                }
            }
            return res;

        };

        public Reservation book(ShowTime showTime, List<String> seatIds){
            if (showTime == null){
                throw new IllegalArgumentException("showTime cannot be null");
            }
            if (seatIds == null || seatIds.isEmpty()){
                throw new IllegalArgumentException("seatIds cannot be null or empty");
            }
            if (LocalDateTime.now().isAfter(showTime.getStartTime())){
                throw new RuntimeException("this show has already begun");
            }
            return showTime.bookSeat(seatIds);
        }

        public void cancel(Reservation reservation){
            if (reservation == null){
                throw new IllegalArgumentException("reservation cannot be null");
            }
            ShowTime showTime = reservation.getShowTime();
            if (LocalDateTime.now().isAfter(showTime.getStartTime())){
                throw new RuntimeException("this show has already begun, we cannot cancel");
            }
            showTime.cancelBooking(reservation);
        }


}
class Theatre {
    private final List<ShowTime> showTimes =  new ArrayList<>();
    public Theatre(){

    }
    public List<ShowTime> getShowTimes() {
        return showTimes;
    }
    public List<ShowTime> getShowTimeByMovie(Movie movie){
        List<ShowTime> result = new ArrayList<>();
        for(ShowTime showTime: showTimes){
            if (showTime.getMovie() == movie){
                result.add(showTime);
            }
        }
        return result;
    }
}

class ShowTime {
    private final Movie movie;
    private final Theatre theatre;
    private final List<Reservation> reservations = new ArrayList<>();
    private final LocalDateTime startTime;

    public ShowTime(Movie movie, Theatre theatre, LocalDateTime startTime) {
        this.movie = movie;
        this.theatre = theatre;
        this.startTime = startTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public Movie getMovie(){
        return movie;
    }



    public Reservation bookSeat(List<String> seatIds) {
        //synchronized block, validate seatID, availability, make state change
        synchronized (this) {

            for (String seatId : seatIds) {
                if (!isValidSeat(seatId)) {
                    throw new IllegalArgumentException("invalid seatID");
                }
                if (!isAvailable(seatId)) {
                    throw new RuntimeException("Seat: %s is not available".formatted(seatId));
                }
            }
            Reservation reservation = new Reservation(this, seatIds);
            this.reservations.add(reservation);
            return reservation;
        }
    }

    public void cancelBooking(Reservation reservation){
        synchronized (this) {
            this.reservations.remove(reservation);
        }
    }

    private boolean isValidSeat(String seatId) {
        if (seatId == null || seatId.length() < 2) {
            return false;
        }
        char row = seatId.charAt(0);
        try {
            int num = Integer.parseInt(seatId.substring(1));
            return row >= 'A' && row <= 'Z' && num >= 0 && num <= 20;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isAvailable(String seatId) {
        if (!isValidSeat(seatId)) {
            throw new IllegalArgumentException("invalid seatID");
        }
        for (Reservation reservation : reservations) {
            List<String> occupiedSeats = reservation.getSeatIds();
            for (String occupiedSeat : occupiedSeats) {
                if (seatId.equals(occupiedSeat)) {
                    return false;
                }
            }
        }
        return true;
    }

}

class Reservation {
    private final ShowTime showTime;
    private final List<String> seatIds;

    public Reservation(ShowTime showTime, List<String> seatIds) {
        this.showTime = showTime;
        this.seatIds = seatIds;
    }

    public List<String> getSeatIds() {
        return seatIds;
    }

    public ShowTime getShowTime(){
        return showTime;
    }
}

class Movie {
    private final String movieTile;
    public Movie(String movieTile) {
        this.movieTile = movieTile;
    }
}
