package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.example.seats.model.Show;
import com.example.seats.model.ShowDetails;
import com.example.seats.repository.ShowRepository;
import com.example.seats.util.Uuids;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ShowService {
    private static final int LIST_LIMIT = 100;

    private final ShowRepository showRepository;
    private final TxRunner tx;

    public ShowService(ShowRepository showRepository, TxRunner tx) {
        this.showRepository = showRepository;
        this.tx = tx;
    }

    public ShowDetails createShow(String name, List<String> seats, long pricePaise, int perUserLimit) {
        UUID showId = UUID.randomUUID();
        Show show = new Show(showId, name, pricePaise, perUserLimit, seats.size());
        tx.inTransaction(() -> {
            showRepository.save(show, seats);
            return null;
        });
        return getShow(showId.toString(), true);
    }

    public ShowDetails getShow(String showIdText, boolean includeSeats) {
        UUID showId = Uuids.tryParse(showIdText);
        if (showId == null) throw showNotFound();

        // one snapshot for counts AND the per-seat list, so they always agree
        return tx.inReadOnlyTransaction(() ->
                showRepository.findDetails(showId, includeSeats).orElseThrow(ShowService::showNotFound));
    }

    public List<Show> listShows() {
        return showRepository.findRecent(LIST_LIMIT);
    }

    private static ApiException showNotFound() {
        return new ApiException(404, "show_not_found", "show not found");
    }
}
