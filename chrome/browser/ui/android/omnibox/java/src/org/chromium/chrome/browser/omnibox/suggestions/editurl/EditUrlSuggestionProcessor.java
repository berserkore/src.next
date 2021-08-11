// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions.editurl;

import android.content.Context;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.base.supplier.Supplier;
import org.chromium.chrome.browser.history_clusters.HistoryClustersTabHelper;
import org.chromium.chrome.browser.omnibox.OmniboxSuggestionType;
import org.chromium.chrome.browser.omnibox.R;
import org.chromium.chrome.browser.omnibox.suggestions.OmniboxSuggestionUiType;
import org.chromium.chrome.browser.omnibox.suggestions.SuggestionHost;
import org.chromium.chrome.browser.omnibox.suggestions.UrlBarDelegate;
import org.chromium.chrome.browser.omnibox.suggestions.base.BaseSuggestionViewProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.base.BaseSuggestionViewProperties.Action;
import org.chromium.chrome.browser.omnibox.suggestions.base.SuggestionDrawableState;
import org.chromium.chrome.browser.omnibox.suggestions.base.SuggestionSpannable;
import org.chromium.chrome.browser.omnibox.suggestions.basic.SuggestionViewProperties;
import org.chromium.chrome.browser.share.ShareDelegate;
import org.chromium.chrome.browser.share.ShareDelegate.ShareOrigin;
import org.chromium.chrome.browser.tab.SadTab;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.favicon.LargeIconBridge;
import org.chromium.components.omnibox.AutocompleteMatch;
import org.chromium.ui.base.Clipboard;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.Arrays;

import org.chromium.chrome.browser.omnibox.UrlBarData;

import org.chromium.base.ContextUtils;

/**
 * This class controls the interaction of the "edit url" suggestion item with the rest of the
 * suggestions list. This class also serves as a mediator, containing logic that interacts with
 * the rest of Chrome.
 */
public class EditUrlSuggestionProcessor extends BaseSuggestionViewProcessor {
    private final Context mContext;

    /** The delegate for accessing the location bar for observation and modification. */
    private final UrlBarDelegate mUrlBarDelegate;

    /** Supplies site favicons. */
    private final Supplier<LargeIconBridge> mIconBridgeSupplier;

    /** The delegate for accessing the sharing feature. */
    private final Supplier<ShareDelegate> mShareDelegateSupplier;

    /** A means of accessing the activity's tab. */
    private final Supplier<Tab> mTabSupplier;

    /** Whether the omnibox has already cleared its content for the focus event. */
    private boolean mHasClearedOmniboxForFocus;

    /**
     * @param locationBarDelegate A means of modifying the location bar.
     */
    public EditUrlSuggestionProcessor(Context context, SuggestionHost suggestionHost,
            UrlBarDelegate locationBarDelegate, Supplier<LargeIconBridge> iconBridgeSupplier,
            Supplier<Tab> tabSupplier, Supplier<ShareDelegate> shareDelegateSupplier) {
        super(context, suggestionHost);

        mContext = context;
        mUrlBarDelegate = locationBarDelegate;
        mIconBridgeSupplier = iconBridgeSupplier;
        mTabSupplier = tabSupplier;
        mShareDelegateSupplier = shareDelegateSupplier;
    }

    @Override
    public boolean doesProcessSuggestion(AutocompleteMatch suggestion, int position) {
        // The what-you-typed suggestion can potentially appear as the second suggestion in some
        // cases. If the first suggestion isn't the one we want, ignore all subsequent suggestions.
        if (position != 0) return false;

        Tab activeTab = mTabSupplier.get();
        if (activeTab == null || !activeTab.isInitialized() || activeTab.isNativePage()
                || SadTab.isShowing(activeTab)) {
            return false;
        }

        if (suggestion.getType() != OmniboxSuggestionType.URL_WHAT_YOU_TYPED
                || !suggestion.getUrl().equals(activeTab.getUrl())) {
            return false;
        }

        if (!mHasClearedOmniboxForFocus) {
            mHasClearedOmniboxForFocus = true;
            if (ContextUtils.getAppSharedPreferences().getBoolean("keep_address_bar_content", false)) {
            UrlBarData parsedToolbar = UrlBarData.forUrlAndText(suggestion.getUrl().getSpec(), suggestion.getUrl().getSpec(), null);
            mUrlBarDelegate.setOmniboxEditingText(parsedToolbar.getEditingOrDisplayText().toString());
            } else {
            mUrlBarDelegate.setOmniboxEditingText("");
            }
        }
        return true;
    }

    @Override
    public int getViewTypeId() {
        return OmniboxSuggestionUiType.EDIT_URL_SUGGESTION;
    }

    @Override
    public PropertyModel createModel() {
        return new PropertyModel(SuggestionViewProperties.ALL_KEYS);
    }

    @Override
    public void populateModel(AutocompleteMatch suggestion, PropertyModel model, int position) {
        super.populateModel(suggestion, model, position);

        model.set(SuggestionViewProperties.TEXT_LINE_1_TEXT,
                new SuggestionSpannable(mTabSupplier.get().getTitle()));
        model.set(SuggestionViewProperties.TEXT_LINE_2_TEXT,
                new SuggestionSpannable(suggestion.getDisplayText()));

        setSuggestionDrawableState(model,
                SuggestionDrawableState.Builder
                        .forDrawableRes(getContext(), R.drawable.ic_globe_24dp)
                        .setAllowTint(true)
                        .build());

        setCustomActions(model,
                Arrays.asList(new Action(mContext,
                                      SuggestionDrawableState.Builder
                                              .forDrawableRes(
                                                      getContext(), R.drawable.ic_share_white_24dp)
                                              .setLarge(true)
                                              .setAllowTint(true)
                                              .build(),
                                      R.string.menu_share_page, this::onShareLink),
                        new Action(mContext,
                                SuggestionDrawableState.Builder
                                        .forDrawableRes(
                                                getContext(), R.drawable.ic_content_copy_black)
                                        .setLarge(true)
                                        .setAllowTint(true)
                                        .build(),
                                R.string.copy_link, () -> onCopyLink(suggestion)),
                        // TODO(https://crbug.com/1090187): do not re-use bookmark_item_edit here.
                        new Action(mContext,
                                SuggestionDrawableState.Builder
                                        .forDrawableRes(
                                                getContext(), R.drawable.bookmark_edit_active)
                                        .setLarge(true)
                                        .setAllowTint(true)
                                        .build(),
                                R.string.bookmark_item_edit, () -> onEditLink(suggestion))));

        fetchSuggestionFavicon(model, suggestion.getUrl(), mIconBridgeSupplier.get(), null);
    }

    @Override
    public void onUrlFocusChange(boolean hasFocus) {
        if (hasFocus) return;
        mHasClearedOmniboxForFocus = false;
    }

    @Override
    protected void onSuggestionClicked(AutocompleteMatch suggestion, int position) {
        super.onSuggestionClicked(suggestion, position);
        RecordUserAction.record("Omnibox.EditUrlSuggestion.Tap");
    }

    /** Invoked when user interacts with Share action button. */
    private void onShareLink() {
        RecordUserAction.record("Omnibox.EditUrlSuggestion.Share");
        mUrlBarDelegate.clearOmniboxFocus();
        // TODO(mdjones): This should only share the displayed URL instead of the background tab.
        mShareDelegateSupplier.get().share(mTabSupplier.get(), false, ShareOrigin.EDIT_URL);
    }

    /** Invoked when user interacts with Copy action button. */
    private void onCopyLink(AutocompleteMatch suggestion) {
        RecordUserAction.record("Omnibox.EditUrlSuggestion.Copy");
        HistoryClustersTabHelper.onCurrentTabUrlCopied(mTabSupplier.get().getWebContents());
        Clipboard.getInstance().copyUrlToClipboard(suggestion.getUrl());
    }

    /** Invoked when user interacts with Edit action button. */
    private void onEditLink(AutocompleteMatch suggestion) {
        RecordUserAction.record("Omnibox.EditUrlSuggestion.Edit");

        UrlBarData parsedToolbar = UrlBarData.forUrlAndText(suggestion.getUrl().getSpec(), suggestion.getUrl().getSpec(), null);
        mUrlBarDelegate.setOmniboxEditingText(parsedToolbar.getEditingOrDisplayText().toString());
    }
}
