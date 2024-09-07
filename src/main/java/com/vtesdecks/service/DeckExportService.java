package com.vtesdecks.service;

import com.vtesdecks.model.DeckExportType;

public interface DeckExportService {

    String export(DeckExportType type, String deckId);
}
