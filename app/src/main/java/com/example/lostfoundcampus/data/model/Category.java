package com.example.lostfoundcampus.data.model;

import androidx.annotation.StringRes;

import com.example.lostfoundcampus.R;

/** Catégories d'objets. Le libellé affiché vient de strings.xml. */
public enum Category {
    PHONES(R.string.category_phones),
    KEYS(R.string.category_keys),
    BAGS(R.string.category_bags),
    WALLETS(R.string.category_wallets),
    CARDS(R.string.category_cards),
    ELECTRONICS(R.string.category_electronics),
    CLOTHES(R.string.category_clothes),
    OTHER(R.string.category_other);

    @StringRes
    public final int labelRes;

    Category(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }
}
