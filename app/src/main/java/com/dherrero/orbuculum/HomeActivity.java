package com.dherrero.orbuculum;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.dherrero.orbuculum.util.IabBroadcastReceiver;
import com.dherrero.orbuculum.util.IabHelper;
import com.dherrero.orbuculum.util.IabResult;
import com.dherrero.orbuculum.util.Inventory;
import com.dherrero.orbuculum.util.Purchase;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class HomeActivity extends AppCompatActivity implements IabBroadcastReceiver.IabBroadcastListener,
    DialogInterface.OnClickListener {

        static final String TAG = "HomeActivity";

        @BindView(R.id.tiptextView)
        TextView tipTextView;

        @BindView(R.id.homeImageView)
        ImageView homeImageView;

        @BindView(R.id.monthlyTipsButton)
        Button infiniteGasButton;

        @BindView(R.id.newTipButton)
        Button newTipButton;


        // The helper object
        IabHelper iabHelper;

        String base64EncodedPublicKey;

        // Provides purchase notification while this app is running
        IabBroadcastReceiver mBroadcastReceiver;

        private static final String SKU_PREMIUM = "premium";
        private static final String SKU_TIP = "tip";
        private static final String SKU_INFINITE_TIPS_MONTHLY = "infinite_tips_monthly";
        private static final String SKU_INFINITE_TIPS_YEARLY = "infinite_tips_yearly";
        // (arbitrary) request code for the purchase flow
        private static final int RC_REQUEST = 10001;

        // Does the user have the premium upgrade?
        boolean mIsPremium = false;
        // Will the subscription auto-renew?
        boolean mAutoRenewEnabled = false;

        // Does the user have an active subscription to the infinite gas plan?
        boolean mSubscribedToInfiniteGas = false;

        String mSelectedSubscriptionPeriod = "";

        // Current amount of gas in tank, in units
        int mTank;

        // How many units (1/4 tank is our unit) fill in the tank.
        static final int TANK_MAX = 4;

        // Tracks the currently owned infinite gas SKU, and the options in the Manage dialog
        String mInfiniteGasSku = "";
        String mFirstChoiceSku = "";
        String mSecondChoiceSku = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ButterKnife.bind(this);

        loadData();

        base64EncodedPublicKey = "add key";

        iabHelper = getIabHelper();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (iabHelper != null) iabHelper.disposeWhenFinished();
        iabHelper = null;
    }

    private IabHelper getIabHelper(){

        // The helper object
        IabHelper mHelper;

        // compute your public key and store it in base64EncodedPublicKey
        mHelper = new IabHelper(this, base64EncodedPublicKey);

        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                getIabSetupFinished(result);
            }
        });

        return mHelper;
    }

    private void getIabSetupFinished(IabResult result){

        if (!result.isSuccess()) {
            // Oh no, there was a problem.
            Log.d(TAG, "Problem setting up In-app Billing: " + result);
            return;
        }

        getBroadCastReceiver();
    }


    private void getBroadCastReceiver(){

        mBroadcastReceiver = new IabBroadcastReceiver(HomeActivity.this);
        IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
        registerReceiver(mBroadcastReceiver, broadcastFilter);
    }


    @OnClick(R.id.newTipButton)
    public void getNewTip(){
        Log.d(TAG, "Buy gas button clicked.");

        if (mSubscribedToInfiniteGas) {
            complain("No need! You're subscribed to infinite gas. Isn't that awesome?");
            return;
        }

        if (mTank >= TANK_MAX) {
            complain("Your tank is full. Drive around a bit!");
            return;
        }

        // launch the gas purchase UI flow.
        // We will be notified of completion via mPurchaseFinishedListener

        Log.d(TAG, "Launching purchase flow for gas.");

        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String payload = "";

        try {
            iabHelper.launchPurchaseFlow(this, SKU_TIP, RC_REQUEST,
                    mPurchaseFinishedListener, payload);
        } catch (IabHelper.IabAsyncInProgressException e) {
            complain("Error launching purchase flow. Another async operation in progress.");

        }

    }


    @OnClick(R.id.monthlyTipsButton)
    public void getMonthlyTips(){

        if (!iabHelper.subscriptionsSupported()) {
            complain("Subscriptions not supported on your device yet. Sorry!");
            return;
        }

        CharSequence[] options;
        if (!mSubscribedToInfiniteGas || !mAutoRenewEnabled) {
            // Both subscription options should be available
            options = new CharSequence[2];
            options[0] = getString(R.string.subscription_period_monthly);
            options[1] = getString(R.string.subscription_period_yearly);
            mFirstChoiceSku = SKU_INFINITE_TIPS_MONTHLY;
            mSecondChoiceSku = SKU_INFINITE_TIPS_YEARLY;
        } else {
            // This is the subscription upgrade/downgrade path, so only one option is valid
            options = new CharSequence[1];
            if (mInfiniteGasSku.equals(SKU_INFINITE_TIPS_MONTHLY)) {
                // Give the option to upgrade to yearly
                options[0] = getString(R.string.subscription_period_yearly);
                mFirstChoiceSku = SKU_INFINITE_TIPS_YEARLY;
            } else {
                // Give the option to downgrade to monthly
                options[0] = getString(R.string.subscription_period_monthly);
                mFirstChoiceSku = SKU_INFINITE_TIPS_MONTHLY;
            }
            mSecondChoiceSku = "";
        }

        int titleResId;
        if (!mSubscribedToInfiniteGas) {
            titleResId = R.string.subscription_period_prompt;
        } else if (!mAutoRenewEnabled) {
            titleResId = R.string.subscription_resignup_prompt;
        } else {
            titleResId = R.string.subscription_update_prompt;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(titleResId)
                .setSingleChoiceItems(options, 0 /* checkedItem */, this)
                .setPositiveButton(R.string.subscription_prompt_continue, this)
                .setNegativeButton(R.string.subscription_prompt_cancel, this);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int id) {

        if (id == 0 /* First choice item */) {
            mSelectedSubscriptionPeriod = mFirstChoiceSku;
        } else if (id == 1 /* Second choice item */) {
            mSelectedSubscriptionPeriod = mSecondChoiceSku;
        } else if (id == DialogInterface.BUTTON_POSITIVE /* continue button */) {
            /* TODO: for security, generate your payload here for verification. See the comments on
             *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
             *        an empty string, but on a production app you should carefully generate
             *        this. */
            String payload = "";

            if (TextUtils.isEmpty(mSelectedSubscriptionPeriod)) {
                // The user has not changed from the default selection
                mSelectedSubscriptionPeriod = mFirstChoiceSku;
            }

            List<String> oldSkus = null;
            if (!TextUtils.isEmpty(mInfiniteGasSku)
                    && !mInfiniteGasSku.equals(mSelectedSubscriptionPeriod)) {
                // The user currently has a valid subscription, any purchase action is going to
                // replace that subscription
                oldSkus = new ArrayList<String>();
                oldSkus.add(mInfiniteGasSku);
            }


            Log.d(TAG, "Launching purchase flow for gas subscription.");
            try {
                iabHelper.launchPurchaseFlow(this, mSelectedSubscriptionPeriod, IabHelper.ITEM_TYPE_SUBS,
                        oldSkus, RC_REQUEST, mPurchaseFinishedListener, payload);
            } catch (IabHelper.IabAsyncInProgressException e) {
                complain("Error launching purchase flow. Another async operation in progress.");

            }
            // Reset the dialog options
            mSelectedSubscriptionPeriod = "";
            mFirstChoiceSku = "";
            mSecondChoiceSku = "";
        } else if (id != DialogInterface.BUTTON_NEGATIVE) {
            // There are only four buttons, this should not happen
            Log.e(TAG, "Unknown button clicked in subscription dialog: " + id);
        }
    }


    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            // if we were disposed of in the meantime, quit.
            if (iabHelper == null) return;

            // We know this is the "gas" sku because it's the only one we consume,
            // so we don't check which sku was consumed. If you have more than one
            // sku, you probably should check...
            if (result.isSuccess()) {
                // successfully consumed, so we apply the effects of the item in our
                // game world's logic, which in our case means filling the gas tank a bit
                Log.d(TAG, "Consumption successful. Provisioning.");
                mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
                saveData();
                alert("You filled 1/4 tank. Your tank is now " + String.valueOf(mTank) + "/4 full!");
            }
            else {
                complain("Error while consuming: " + result);
            }
            updateUi();
            Log.d(TAG, "End consumption flow.");
        }
    };

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (iabHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                complain("Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(SKU_PREMIUM);
            mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
            Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

            // First find out which subscription is auto renewing
            Purchase gasMonthly = inventory.getPurchase(SKU_INFINITE_TIPS_MONTHLY);
            Purchase gasYearly = inventory.getPurchase(SKU_INFINITE_TIPS_YEARLY);
            if (gasMonthly != null && gasMonthly.isAutoRenewing()) {
                mInfiniteGasSku = SKU_INFINITE_TIPS_MONTHLY;
                mAutoRenewEnabled = true;
            } else if (gasYearly != null && gasYearly.isAutoRenewing()) {
                mInfiniteGasSku = SKU_INFINITE_TIPS_YEARLY;
                mAutoRenewEnabled = true;
            } else {
                mInfiniteGasSku = "";
                mAutoRenewEnabled = false;
            }

            // The user is subscribed if either subscription exists, even if neither is auto
            // renewing
            mSubscribedToInfiniteGas = (gasMonthly != null && verifyDeveloperPayload(gasMonthly))
                    || (gasYearly != null && verifyDeveloperPayload(gasYearly));
            Log.d(TAG, "User " + (mSubscribedToInfiniteGas ? "HAS" : "DOES NOT HAVE")
                    + " infinite gas subscription.");
            if (mSubscribedToInfiniteGas) mTank = TANK_MAX;

            // Check for gas delivery -- if we own gas, we should fill up the tank immediately
            Purchase gasPurchase = inventory.getPurchase(SKU_TIP);
            if (gasPurchase != null && verifyDeveloperPayload(gasPurchase)) {
                Log.d(TAG, "We have gas. Consuming it.");
                try {
                    iabHelper.consumeAsync(inventory.getPurchase(SKU_TIP), mConsumeFinishedListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    complain("Error consuming gas. Another async operation in progress.");
                }
                return;
            }

            updateUi();
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (iabHelper == null) return;

            if (result.isFailure()) {
                complain("Error purchasing: " + result);

                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");

                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_TIP)) {
                // bought 1/4 tank of gas. So consume it.
                Log.d(TAG, "Purchase is gas. Starting gas consumption.");
                try {
                    iabHelper.consumeAsync(purchase, mConsumeFinishedListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    complain("Error consuming gas. Another async operation in progress.");

                    return;
                }
            }
            else if (purchase.getSku().equals(SKU_PREMIUM)) {
                // bought the premium upgrade!
                Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                alert("Thank you for upgrading to premium!");
                mIsPremium = true;
                updateUi();

            }
            else if (purchase.getSku().equals(SKU_INFINITE_TIPS_MONTHLY)
                    || purchase.getSku().equals(SKU_INFINITE_TIPS_YEARLY)) {
                // bought the infinite gas subscription
                Log.d(TAG, "Infinite gas subscription purchased.");
                alert("Thank you for subscribing to infinite gas!");
                mSubscribedToInfiniteGas = true;
                mAutoRenewEnabled = purchase.isAutoRenewing();
                mInfiniteGasSku = purchase.getSku();
                mTank = TANK_MAX;
                updateUi();
            }
        }
    };

    /** Verifies the developer payload of a purchase. */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    @Override
    public void receivedBroadcast() {

        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            iabHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            complain("Error querying inventory. Another async operation in progress.");
        }


    }


    // updates UI to reflect model
    public void updateUi() {
        // update the car color to reflect premium status or lack thereof
        homeImageView.setImageResource(mIsPremium ? R.drawable.premium : R.drawable.free);

        // "Upgrade" button is only visible if the user is not premium
        newTipButton.setVisibility(mIsPremium ? View.GONE : View.VISIBLE);

        if (mSubscribedToInfiniteGas) {
            // If subscription is active, show "Manage Infinite Gas"

            infiniteGasButton.setText(R.string.purchase_monthly_tips_btn_active);
        } else {
            // The user does not have infinite gas, show "Get Infinite Gas"

            infiniteGasButton.setText(R.string.purchase_monthly_tips_btn);
        }

//        // update gas gauge to reflect tank status
//        if (mSubscribedToInfiniteGas) {
//            ((ImageView)findViewById(R.id.gas_gauge)).setImageResource(R.drawable.gas_inf);
//        }
//        else {
//            int index = mTank >= TANK_RES_IDS.length ? TANK_RES_IDS.length - 1 : mTank;
//            ((ImageView)findViewById(R.id.gas_gauge)).setImageResource(TANK_RES_IDS[index]);
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (iabHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!iabHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }


    void saveData() {

        /*
         * WARNING: on a real application, we recommend you save data in a secure way to
         * prevent tampering. For simplicity in this sample, we simply store the data using a
         * SharedPreferences.
         */

        SharedPreferences.Editor spe = getPreferences(MODE_PRIVATE).edit();
        spe.putInt("tank", mTank);
        spe.apply();
        Log.d(TAG, "Saved data: tank = " + String.valueOf(mTank));
    }

    void loadData() {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        mTank = sp.getInt("tank", 2);
        Log.d(TAG, "Loaded data: tank = " + String.valueOf(mTank));
    }


    void complain(String message) {
        Log.e(TAG, "**** Orbuculum Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }





}
