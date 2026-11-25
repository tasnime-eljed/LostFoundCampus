package com.example.lostfoundcampus.ui.messages;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.ui.map.MapHelper;
import com.example.lostfoundcampus.util.DateUtil;

/**
 * Message d'introduction proposé en brouillon au début d'une conversation :
 * modèle de texte local (strings.xml) rempli avec les détails de l'annonce. Jamais envoyé sans action.
 */
final class IntroMessageTemplate {

    private IntroMessageTemplate() {
    }

    @NonNull
    static String build(@NonNull Context c, @NonNull Announcement a, @NonNull User recipient) {
        String place = MapHelper.label(c, a.location);
        if (a.type == AnnouncementType.FOUND) {
            // L'autre personne a trouvé l'objet : je pense qu'il est à moi.
            return c.getString(R.string.intro_template_found, recipient.firstName(), a.title, place,
                    DateUtil.shortDate(a.eventDate));
        }
        // L'autre personne a perdu l'objet : je l'ai peut-être trouvé.
        return c.getString(R.string.intro_template_lost, recipient.firstName(), a.title, place);
    }
}
