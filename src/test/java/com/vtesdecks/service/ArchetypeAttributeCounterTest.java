package com.vtesdecks.service;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.service.ArchetypeAttributeCounter.Key;
import com.vtesdecks.util.VtesUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.vtesdecks.model.ArchetypeAttributeRequirement.Type.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchetypeAttributeCounterTest {
    @Test void countsCopiesOncePerAttributeAndIgnoresMissingOrZeroCards() {
        var cryptCache = mock(CryptCache.class);
        var libraryCache = mock(LibraryCache.class);
        var basic = mock(Crypt.class);
        var superior = mock(Crypt.class);
        when(cryptCache.get(200001)).thenReturn(basic);
        when(cryptCache.get(200002)).thenReturn(superior);
        when(basic.getClan()).thenReturn("Malkavian");
        when(superior.getClan()).thenReturn("Malkavian antitribu");
        when(basic.getDisciplines()).thenReturn(VtesUtils.getCryptDisciplineNames("Vampire", "dom", false));
        when(superior.getDisciplines()).thenReturn(VtesUtils.getCryptDisciplineNames("Vampire", "DOM", false));
        var library = mock(Library.class);
        when(libraryCache.get(100001)).thenReturn(library);
        when(library.getTypes()).thenReturn(Set.of("Action", "Combat"));
        when(library.getDisciplines()).thenReturn(Set.of("Dominate", "Obfuscate"));
        var counts = ArchetypeAttributeCounter.count(Map.of(200001, 3, 200002, 2, 100001, 5, 100002, 0, 999999, 9), cryptCache, libraryCache);
        assertEquals(3L, counts.get(new Key(CRYPT_CLAN, "Malkavian")));
        assertEquals(2L, counts.get(new Key(CRYPT_CLAN, "Malkavian antitribu")));
        assertEquals(5L, counts.get(new Key(CRYPT_DISCIPLINE, "Dominate")));
        assertEquals(5L, counts.get(new Key(LIBRARY_TYPE, "Action")));
        assertEquals(5L, counts.get(new Key(LIBRARY_TYPE, "Combat")));
        assertEquals(5L, counts.get(new Key(LIBRARY_DISCIPLINE, "Dominate")));
        verify(libraryCache, never()).get(100002);
        assertFalse(counts.containsKey(new Key(LIBRARY_TYPE, "Political Action")));
    }
}
