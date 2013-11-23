package au.com.codeka.warworlds.game.starfield;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

import org.andengine.engine.camera.hud.HUD;
import org.andengine.entity.Entity;
import org.andengine.entity.primitive.DrawMode;
import org.andengine.entity.primitive.Mesh;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.sprite.Sprite;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.font.Font;
import org.andengine.opengl.font.FontFactory;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.ITextureAtlas.ITextureAtlasStateListener;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.atlas.bitmap.BuildableBitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.source.IBitmapTextureAtlasSource;
import org.andengine.opengl.texture.atlas.buildable.builder.BlackPawnTextureAtlasBuilder;
import org.andengine.opengl.texture.atlas.buildable.builder.ITextureAtlasBuilder.TextureAtlasBuilderException;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.opengl.texture.region.TiledTextureRegion;
import org.andengine.opengl.vbo.VertexBufferObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import au.com.codeka.common.Pair;
import au.com.codeka.common.PointCloud;
import au.com.codeka.common.Triangle;
import au.com.codeka.common.Vector2;
import au.com.codeka.common.Voronoi;
import au.com.codeka.common.model.BaseColony;
import au.com.codeka.common.model.BaseFleet;
import au.com.codeka.common.model.BaseStar;
import au.com.codeka.controlfield.ControlField;
import au.com.codeka.warworlds.model.BuildManager;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.EmpireShieldManager;
import au.com.codeka.warworlds.model.Fleet;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.Sector;
import au.com.codeka.warworlds.model.SectorManager;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarManager;

/**
 * \c SurfaceView that displays the starfield. You can scroll around, tap on stars to bring
 * up their details and so on.
 */
public class StarfieldSceneManager extends SectorSceneManager
                                  implements StarManager.StarFetchedHandler,
                                             EmpireManager.EmpireFetchedHandler {
    private static final Logger log = LoggerFactory.getLogger(StarfieldSceneManager.class);
    private ArrayList<OnSelectionChangedListener> mSelectionChangedListeners;
    private BaseStar mHqStar;
    private Handler mHandler;
    private boolean mHasScrolled;

    private SelectableEntity mSelectingEntity;
    private SelectionIndicatorEntity mSelectionIndicator;
    private RadarIndicatorEntity mRadarIndicator;
    private boolean mWasDragging;

    private Font mFont;
    private BitmapTextureAtlas mStarTextureAtlas;
    private TiledTextureRegion mNeutronStarTextureRegion;
    private TiledTextureRegion mNormalStarTextureRegion;

    private BuildableBitmapTextureAtlas mIconTextureAtlas;
    private ITextureRegion mArrowIconTextureRegion;

    private BuildableBitmapTextureAtlas mFleetSpriteTextureAtlas;
    private HashMap<String, ITextureRegion> mFleetSpriteTextures;

    private BitmapTextureAtlas mBackgroundGasTextureAtlas;
    private TiledTextureRegion mBackgroundGasTextureRegion;
    private BitmapTextureAtlas mBackgroundStarsTextureAtlas;
    private TiledTextureRegion mBackgroundStarsTextureRegion;
    private ArrayList<Entity> mBackgroundEntities;
    private boolean mIsBackgroundVisible = true;;
    private float mBackgroundZoomAlpha = 1.0f;

    private Map<String, StarEntity> mStars;
    private Map<String, FleetEntity> mFleets;
    private StarEntity mSelectedStarEntity;
    private FleetEntity mSelectedFleetEntity;
    private String mStarToSelect;

    private TacticalPointCloud mPointCloud;
    private TreeMap<String, TacticalControlField> mControlFields;
    private boolean mIsTacticalVisible;
    private float mTacticalZoomAlpha;

    public StarfieldSceneManager(BaseStarfieldActivity activity) {
        super(activity);
        log.info("Starfield initializing...");

        mSelectionChangedListeners = new ArrayList<OnSelectionChangedListener>();
        mHandler = new Handler();
    }

    @Override
    public void onLoadResources() {
        mStarTextureAtlas = new BitmapTextureAtlas(mActivity.getTextureManager(), 128, 384,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mStarTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mNormalStarTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mStarTextureAtlas, mActivity,
                "stars/stars_small.png", 0, 0, 2, 6);
        mNeutronStarTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mStarTextureAtlas, mActivity,
                "stars/stars_small.png", 0, 0, 1, 3);

        mBackgroundGasTextureAtlas = new BitmapTextureAtlas(mActivity.getTextureManager(), 512, 512,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mBackgroundGasTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mBackgroundGasTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mBackgroundGasTextureAtlas,
                mActivity, "decoration/gas.png", 0, 0, 4, 4);
        mBackgroundStarsTextureAtlas = new BitmapTextureAtlas(mActivity.getTextureManager(), 512, 512,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mBackgroundStarsTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mBackgroundStarsTextureAtlas,
                mActivity, "decoration/starfield.png", 0, 0, 4, 4);

        mIconTextureAtlas = new BuildableBitmapTextureAtlas(mActivity.getTextureManager(), 256, 256,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mIconTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mArrowIconTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(mIconTextureAtlas, mActivity, "img/arrow.png");

        mFleetSpriteTextureAtlas = new BuildableBitmapTextureAtlas(mActivity.getTextureManager(), 256, 256,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mFleetSpriteTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mFleetSpriteTextures = new HashMap<String, ITextureRegion>();
        mFleetSpriteTextures.put("ship.fighter", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.fighter.png"));
        mFleetSpriteTextures.put("ship.scout", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.scout.png"));
        mFleetSpriteTextures.put("ship.colony", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.colony.png"));
        mFleetSpriteTextures.put("ship.troopcarrier", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.troopcarrier.png"));

        mActivity.getShaderProgramManager().loadShaderProgram(RadarIndicatorEntity.getShaderProgram());
        mActivity.getTextureManager().loadTexture(mStarTextureAtlas);
        mActivity.getTextureManager().loadTexture(mBackgroundGasTextureAtlas);
        mActivity.getTextureManager().loadTexture(mBackgroundStarsTextureAtlas);

        try {
            BlackPawnTextureAtlasBuilder<IBitmapTextureAtlasSource, BitmapTextureAtlas> builder =
                    new BlackPawnTextureAtlasBuilder<IBitmapTextureAtlasSource, BitmapTextureAtlas>(1, 1, 1);
            mIconTextureAtlas.build(builder);
            mIconTextureAtlas.load();

            mFleetSpriteTextureAtlas.build(builder);
            mFleetSpriteTextureAtlas.load();
        } catch (TextureAtlasBuilderException e) {
            log.error("Error building texture atlas.", e);
        }

        mFont = FontFactory.create(mActivity.getFontManager(), mActivity.getTextureManager(), 256, 256,
                                   Typeface.create(Typeface.DEFAULT, Typeface.NORMAL), 16, true, Color.WHITE);
        mFont.load();

        mSelectionIndicator = new SelectionIndicatorEntity(this);
        mRadarIndicator = new RadarIndicatorEntity(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        StarManager.getInstance().addStarUpdatedListener(null, this);
        EmpireManager.i.addEmpireUpdatedListener(null, this);

        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (myEmpire != null) {
            BaseStar homeStar = myEmpire.getHomeStar();
            int numHqs = BuildManager.getInstance().getTotalBuildingsInEmpire("hq");
            if (numHqs > 0) {
                mHqStar = homeStar;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        StarManager.getInstance().removeStarUpdatedListener(this);
        EmpireManager.i.removeEmpireUpdatedListener(this);
    }

    public Font getFont() {
        return mFont;
    }

    public ITextureRegion getSpriteTexture(String spriteName) {
        return mFleetSpriteTextures.get(spriteName);
    }

    public ITextureRegion getArrowTexture() {
        return mArrowIconTextureRegion;
    }

    public void addSelectionChangedListener(OnSelectionChangedListener listener) {
        if (!mSelectionChangedListeners.contains(listener)) {
            mSelectionChangedListeners.add(listener);
        }
    }

    public void removeSelectionChangedListener(OnSelectionChangedListener listener) {
        mSelectionChangedListeners.remove(listener);
    }

    protected void fireSelectionChanged(final Star star) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for(OnSelectionChangedListener listener : mSelectionChangedListeners) {
                    listener.onStarSelected(star);
                }
            }
        });
    }
    protected void fireSelectionChanged(final Fleet fleet) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for(OnSelectionChangedListener listener : mSelectionChangedListeners) {
                    listener.onFleetSelected(fleet);
                }
            }
        });
    }

    @Override
    public void scrollTo(final long sectorX, final long sectorY,
            final float offsetX, final float offsetY,
            final boolean centre) {
        log.debug("==SCROLL TO: "+sectorX+","+sectorY);
        mHasScrolled = true;
        super.scrollTo(sectorX, sectorY, offsetX, offsetY, centre);
    }

    @Override
    public void onEmpireFetched(Empire empire) {
        // if the player's empire changes, it might mean that the location of their HQ has changed,
        // so we'll want to make sure it's still correct.
        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (empire.getKey().equals(myEmpire.getKey())) {
            if (mHqStar != null) {
                mHqStar = empire.getHomeStar();
            }
        }

        // otherwise, refresh the scene (but only if it's one we're actually displaying...)
        if (mControlFields.keySet().contains(empire.getKey())) {
            refreshScene();
        }
    }

    public Star getSelectedStar() {
        return null;
    }

    @Override
    protected void refreshScene(Scene scene) {
        if (!mHasScrolled) {
            // if you haven't scrolled yet, then don't even think about refreshing the
            // scene... it's a waste of time!
            log.debug("We haven't scrolled yet, not drawing the scene.");
            return;
        }

        if (mActivity.getEngine() == null) {
            // if the engine hasn't been created yet, schedule a refresh for later...
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    refreshScene();
                }
            });
            return;
        }

        mFleets = new HashMap<String, FleetEntity>();
        mStars = new HashMap<String, StarEntity>();
        final List<Pair<Long, Long>> missingSectors = drawScene(scene);
        if (missingSectors != null) {
            SectorManager.getInstance().requestSectors(missingSectors, false, null);
        }

        refreshSelectionIndicator();
    }

    @Override
    protected void refreshHud(HUD hud) {
        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (myEmpire != null && myEmpire.getHomeStar() != null) {
            // if you have a HQ, it'll be on your home star.
            if (BuildManager.getInstance().getTotalBuildingsInEmpire("hq") > 0) {
                hud.attachChild(new HqEntity(this, myEmpire.getHomeStar(), mActivity.getCamera(),
                        mActivity.getVertexBufferObjectManager()));
            }
        }
    }

    @Override
    protected void updateZoomFactor(float zoomFactor) {
        super.updateZoomFactor(zoomFactor);

        // we fade out the background between 0.55 and 0.50, it should be totally invisible < 0.50
        // and totally opaque for >= 0.55
        if (zoomFactor < 0.5f && mIsBackgroundVisible) {
            mIsBackgroundVisible = false;
            // we need to make the background as invisible
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    for (Entity entity : mBackgroundEntities) {
                        entity.setVisible(mIsBackgroundVisible);
                    }
                }
            });
        } else if (zoomFactor >= 0.5f && !mIsBackgroundVisible) {
            mIsBackgroundVisible = true;
            // we need to make the background as visible
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    for (Entity entity : mBackgroundEntities) {
                        entity.setVisible(mIsBackgroundVisible);
                    }
                }
            });
        }
        if (zoomFactor >= 0.5f && zoomFactor < 0.55f) {
            // between 0.5 and 0.55 we need to fade the background in
            mBackgroundZoomAlpha = (zoomFactor - 0.5f) * 20.0f; // make it in the range 0...1
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    for (Entity entity : mBackgroundEntities) {
                        entity.setAlpha(mBackgroundZoomAlpha);
                        entity.setColor(mBackgroundZoomAlpha, mBackgroundZoomAlpha, mBackgroundZoomAlpha);
                    }
                }
            });
        }

        // similarly, we fade IN the tactical view as you zoom out. It starts fading in a bit sooner
        // than the background fades out, and fades slower, too.
        if (zoomFactor >= 0.6f && mIsTacticalVisible) {
            mIsTacticalVisible = false;
            mTacticalZoomAlpha = 0.0f;
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    for (TacticalControlField tcf : mControlFields.values()) {
                        tcf.updateAlpha(mIsTacticalVisible, mTacticalZoomAlpha);
                    }
                }
            });
        } else if (zoomFactor < 0.4f && !mIsTacticalVisible) {
            mIsTacticalVisible = true;
            mTacticalZoomAlpha = 1.0f;
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    for (TacticalControlField tcf : mControlFields.values()) {
                        tcf.updateAlpha(mIsTacticalVisible, mTacticalZoomAlpha);
                    }
                }
            });
        }
        if (zoomFactor >= 0.4f && zoomFactor < 0.6f) {
            mIsTacticalVisible = true;
            mTacticalZoomAlpha = 1.0f - ((zoomFactor - 0.4f) * 5.0f); // make it 1...0
            mActivity.runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    for (TacticalControlField tcf : mControlFields.values()) {
                        tcf.updateAlpha(mIsTacticalVisible, mTacticalZoomAlpha);
                    }
                }
            });
        }
    }

    private List<Pair<Long, Long>> drawScene(Scene scene) {
        SectorManager sm = SectorManager.getInstance();
        List<Pair<Long, Long>> missingSectors = null;

        mBackgroundEntities = new ArrayList<Entity>();

        for(int y = -mSectorRadius; y <= mSectorRadius; y++) {
            for(int x = -mSectorRadius; x <= mSectorRadius; x++) {
                long sX = mSectorX + x;
                long sY = mSectorY + y;
                Sector sector = sm.getSector(sX, sY);
                if (sector == null) {
                    if (missingSectors == null) {
                        missingSectors = new ArrayList<Pair<Long, Long>>();
                    }
                    missingSectors.add(new Pair<Long, Long>(sX, sY));
                    continue;
                }

                int sx = (int)(x * Sector.SECTOR_SIZE);
                int sy = -(int)(y * Sector.SECTOR_SIZE);
                drawBackground(scene, sector, sx, sy);
            }
        }

        addTacticalView(scene);

        for (int y = -mSectorRadius; y <= mSectorRadius; y++) {
            for(int x = -mSectorRadius; x <= mSectorRadius; x++) {
                long sX = mSectorX + x;
                long sY = mSectorY + y;

                Sector sector = sm.getSector(sX, sY);
                if (sector == null) {
                    continue;
                }

                int sx = (int)(x * Sector.SECTOR_SIZE);
                int sy = -(int)(y * Sector.SECTOR_SIZE);
                addSector(scene, sx, sy, sector);
            }
        }

        return missingSectors;
    }

    private void drawBackground(Scene scene, Sector sector, int sx, int sy) {
        Random r = new Random(sector.getX() ^ (long)(sector.getY() * 48647563));
        final int STAR_SIZE = 256;
        for (int y = 0; y < Sector.SECTOR_SIZE / STAR_SIZE; y++) {
            for (int x = 0; x < Sector.SECTOR_SIZE / STAR_SIZE; x++) {
                Sprite bgSprite = new Sprite(
                        (float) (sx + (x * STAR_SIZE)),
                        (float) (sy + (y * STAR_SIZE)),
                        STAR_SIZE, STAR_SIZE,
                        mBackgroundStarsTextureRegion.getTextureRegion(r.nextInt(16)),
                        mActivity.getVertexBufferObjectManager());
                setBackgroundEntityZoomFactor(bgSprite);
                scene.attachChild(bgSprite);
                mBackgroundEntities.add(bgSprite);
            }
        }

        final int GAS_SIZE = 512;
        for (int i = 0; i < 10; i++) {
            float x = r.nextInt(Sector.SECTOR_SIZE + (GAS_SIZE / 4)) - (GAS_SIZE / 8);
            float y = r.nextInt(Sector.SECTOR_SIZE + (GAS_SIZE / 4)) - (GAS_SIZE / 8);

            Sprite bgSprite = new Sprite(
                    (sx + x) - (GAS_SIZE / 2.0f),
                    (sy + y) - (GAS_SIZE / 2.0f),
                    GAS_SIZE, GAS_SIZE,
                    mBackgroundGasTextureRegion.getTextureRegion(r.nextInt(14)),
                    mActivity.getVertexBufferObjectManager());
            setBackgroundEntityZoomFactor(bgSprite);
            scene.attachChild(bgSprite);
            mBackgroundEntities.add(bgSprite);
        }
    }

    private void setBackgroundEntityZoomFactor(Sprite bgSprite) {
        if (mBackgroundZoomAlpha <= 0.0f) {
            bgSprite.setVisible(false);
        } else if (mBackgroundZoomAlpha >= 1.0f) {
            // do nothing
        } else {
            bgSprite.setAlpha(mBackgroundZoomAlpha);
            bgSprite.setColor(mBackgroundZoomAlpha, mBackgroundZoomAlpha, mBackgroundZoomAlpha);
        }
    }

    /**
     * Draws a sector, which is a 1024x1024 area of stars.
     */
    private void addSector(Scene scene, int offsetX, int offsetY, Sector sector) {
        for(BaseStar star : sector.getStars()) {
            addStar(scene, (Star) star, offsetX, offsetY);
        }
        for (BaseStar star : sector.getStars()) {
            for (BaseFleet fleet : star.getFleets()) {
                if (fleet.getState() == Fleet.State.MOVING) {
                    addMovingFleet(scene, (Fleet) fleet, (Star) star, offsetX, offsetY);
                }
            }
        }
    }

    /**
     * Draws a single star. Note that we draw all stars first, then the names of stars
     * after.
     */
    private void addStar(Scene scene, Star star, int x, int y) {
        x += star.getOffsetX();
        y += Sector.SECTOR_SIZE - star.getOffsetY();

        ITextureRegion textureRegion = null;
        if (star.getStarType().getInternalName().equals("neutron")) {
            textureRegion = mNeutronStarTextureRegion.getTextureRegion(0);
        } else {
            int offset = 0;
            if (star.getStarType().getInternalName().equals("black-hole")) {
                offset = 4;
            } else if (star.getStarType().getInternalName().equals("blue")) {
                offset = 5;
            } else if (star.getStarType().getInternalName().equals("orange")) {
                offset = 6;
            } else if (star.getStarType().getInternalName().equals("red")) {
                offset = 7;
            } else if (star.getStarType().getInternalName().equals("white")) {
                offset = 8;
            } else if (star.getStarType().getInternalName().equals("yellow")) {
                offset = 9;
            }
            textureRegion = mNormalStarTextureRegion.getTextureRegion(offset);
        }

        StarEntity starEntity = new StarEntity(this, star,
                                               (float) x, (float) y,
                                               textureRegion, mActivity.getVertexBufferObjectManager());
        scene.registerTouchArea(starEntity.getTouchEntity());
        scene.attachChild(starEntity);
        mStars.put(star.getKey(), starEntity);

        if (mSelectedStarEntity != null && mSelectedStarEntity.getStar().getKey().equals(star.getKey())) {
            // the selected star will have been refreshed from the server with full details (buildings, etc), whereas
            // the one in the sector will just be a summary. We want to make sure the selection stays "full detail".
            Star selectedStar = mSelectedStarEntity.getStar();
            mSelectedStarEntity = starEntity;
            mSelectedStarEntity.setStar(selectedStar);
        }
        if (mStarToSelect != null && mStarToSelect.equals(star.getKey())) {
            mStarToSelect = null;
            mSelectedStarEntity = starEntity;
        }
    }

    /**
     * Given a \c Sector, returns the (x, y) coordinates (in view-space) of the origin of this
     * sector.
     */
    public Vector2 getSectorOffset(long sx, long sy) {
        sx -= mSectorX;
        sy -= mSectorY;
        return Vector2.pool.borrow().reset((sx * Sector.SECTOR_SIZE),
                                           -(sy * Sector.SECTOR_SIZE));
    }

    /**
     * Draw a moving fleet as a line between the source and destination stars, with an icon
     * representing the current location of the fleet.
     */
    private void addMovingFleet(Scene scene, Fleet fleet, Star srcStar, int offsetX, int offsetY) {
        // we'll need to find the destination star
        Star destStar = SectorManager.getInstance().findStar(fleet.getDestinationStarKey());
        if (destStar == null) {
            // the destination star isn't in one of the sectors we have in memory, we'll
            // just ignore this fleet (it's probably flying off the edge of the sector and our
            // little viewport won't see it anyway -- unless you've got a REALLY long-range
            // flight, maybe we can stop that from being possible).
            return;
        }

        Vector2 srcPoint = Vector2.pool.borrow().reset(offsetX, offsetY);
        srcPoint.x += srcStar.getOffsetX();
        srcPoint.y += Sector.SECTOR_SIZE - srcStar.getOffsetY();

        Vector2 destPoint = getSectorOffset(destStar.getSectorX(), destStar.getSectorY());
        destPoint.x += destStar.getOffsetX();
        destPoint.y += Sector.SECTOR_SIZE - destStar.getOffsetY();

        FleetEntity fleetEntity = new FleetEntity(this, srcPoint, destPoint, fleet, mActivity.getVertexBufferObjectManager());
        scene.registerTouchArea(fleetEntity.getTouchEntity());
        scene.attachChild(fleetEntity);
        mFleets.put(fleet.getKey(), fleetEntity);

        if (mSelectedFleetEntity != null && mSelectedFleetEntity.getFleet().getKey().equals(fleet.getKey())) {
            mSelectedFleetEntity = fleetEntity;
        }
    }

    Collection<FleetEntity> getMovingFleets() {
        return mFleets.values();
    }

    private void addTacticalView(Scene scene) {
        SectorManager sm = SectorManager.getInstance();

        ArrayList<Vector2> points = new ArrayList<Vector2>();
        TreeMap<String, List<Vector2>> empirePoints = new TreeMap<String, List<Vector2>>();

        for(int y = -mSectorRadius; y <= mSectorRadius; y++) {
            for(int x = -mSectorRadius; x <= mSectorRadius; x++) {
                long sX = mSectorX + x;
                long sY = mSectorY + y;

                Sector sector = sm.getSector(sX, sY);
                if (sector == null) {
                    continue;
                }

                int sx = (int)(x * Sector.SECTOR_SIZE);
                int sy = -(int)(y * Sector.SECTOR_SIZE);

                for (BaseStar star : sector.getStars()) {
                    int starX = sx + star.getOffsetX();
                    int starY = sy + (Sector.SECTOR_SIZE - star.getOffsetY());
                    Vector2 pt = new Vector2((float) starX / Sector.SECTOR_SIZE, (float) starY / Sector.SECTOR_SIZE);

                    TreeSet<String> doneEmpires = new TreeSet<String>();
                    for (BaseColony c : star.getColonies()) {
                        String empireKey = c.getEmpireKey();
                        if (empireKey == null || empireKey.length() == 0) {
                            continue;
                        }
                        if (doneEmpires.contains(empireKey)) {
                            continue;
                        }
                        doneEmpires.add(empireKey);
                        List<Vector2> thisEmpirePoints = empirePoints.get(empireKey);
                        if (thisEmpirePoints == null) {
                            thisEmpirePoints = new ArrayList<Vector2>();
                            empirePoints.put(empireKey, thisEmpirePoints);
                        }
                        thisEmpirePoints.add(pt);
                    }
                    points.add(pt);
                }
            }
        }

        mControlFields = new TreeMap<String, TacticalControlField>();
        mPointCloud = new TacticalPointCloud(points);
        TacticalVoronoi v = new TacticalVoronoi(mPointCloud);

        for (String empireKey : empirePoints.keySet()) {
            TacticalControlField cf = new TacticalControlField(mPointCloud, v);

            List<Vector2> pts = empirePoints.get(empireKey);
            for (Vector2 pt : pts) {
                cf.addPointToControlField(pt);
            }

            int colour = Color.RED;
            Empire empire = EmpireManager.i.getEmpire(empireKey);
            if (empire != null) {
                colour = EmpireShieldManager.i.getShieldColour(empire);
            } else {
                final String theEmpireKey = empireKey;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        EmpireManager.i.refreshEmpire(theEmpireKey);
                    }
                });
            }
            cf.addToScene(scene, getActivity().getVertexBufferObjectManager(), colour);
            mControlFields.put(empireKey, cf);
        }
    }

    @Override
    public boolean onSceneTouchEvent(Scene scene, TouchEvent touchEvent) {
        boolean handled = super.onSceneTouchEvent(scene, touchEvent);

        if (touchEvent.getAction() == TouchEvent.ACTION_DOWN) {
            mWasDragging = false;
        } else if (touchEvent.getAction() == TouchEvent.ACTION_UP) {
            if (!mWasDragging) {
                selectNothing();
                handled = true;
            }
        }

        return handled;
    }

    @Override
    protected GestureDetector.OnGestureListener createGestureListener() {
        return new GestureListener();
    }

    @Override
    protected ScaleGestureDetector.OnScaleGestureListener createScaleGestureListener() {
        return new ScaleGestureListener();
    }

    /** The default gesture listener is just for scrolling around. */
    protected class GestureListener extends SectorSceneManager.GestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                float distanceY) {
            super.onScroll(e1, e2, distanceX, distanceY);

            // because we've navigating the map, we're no longer in the process of selecting a sprite.
            mSelectingEntity = null;
            mWasDragging = true;
            return true;
        }
    }

    /** The default scale gesture listener scales the view. */
    protected class ScaleGestureListener extends SectorSceneManager.ScaleGestureListener {
        @Override
        public boolean onScale (ScaleGestureDetector detector) {
            super.onScale(detector);

            // because we've navigating the map, we're no longer in the process of selecting a sprite.
            mSelectingEntity = null;
            mWasDragging = true;
            return true;
        }
    }

    /** Gets the sprite we've marked as "being selected". That is, you've tapped down, but not yet tapped up. */
    public SelectableEntity getSelectingEntity() {
        return mSelectingEntity;
    }

    /** Sets the sprite that we've tapped down on, but not yet tapped up on. */
    public void setSelectingEntity(SelectableEntity entity) {
        mSelectingEntity = entity;
    }

    public void selectStar(StarEntity selectedStarEntity) {
        mSelectedStarEntity = selectedStarEntity;
        mSelectedFleetEntity = null;

        refreshSelectionIndicator();
        if (mSelectedStarEntity == null) {
            fireSelectionChanged((Star) null);
        } else {
            fireSelectionChanged(mSelectedStarEntity.getStar());
        }
    }

    public void selectStar(String starKey) {
        if (mStars == null) {
            // this can happen if we haven't refreshed the scene yet.
            mStarToSelect = starKey;
            return;
        }

        if (starKey == null || !mStars.containsKey(starKey)) {
            selectStar((StarEntity) null);
            return;
        }

        selectStar(mStars.get(starKey));
    }

    public void selectFleet(FleetEntity fleet) {
        mSelectedStarEntity = null;
        mSelectedFleetEntity = fleet;

        refreshSelectionIndicator();
        fireSelectionChanged(mSelectedFleetEntity == null ? null : mSelectedFleetEntity.getFleet());
    }

    public void selectFleet(String fleetKey) {
        if (fleetKey == null) {
            selectFleet((FleetEntity) null);
            return;
        }

        selectFleet(mFleets.get(fleetKey));
    }

    /** Deselects the fleet or star you currently have selected. */
    public void selectNothing() {
        if (mSelectedStarEntity != null) {
            mSelectedStarEntity = null;
            refreshSelectionIndicator();
            fireSelectionChanged((Star) null);
        }

        if (mSelectedFleetEntity != null) {
            mSelectedFleetEntity = null;
            refreshSelectionIndicator();
            fireSelectionChanged((Fleet) null);
        }
    }

    private void refreshSelectionIndicator() {
        if (mSelectionIndicator.getParent() != null) {
            mSelectionIndicator.getParent().detachChild(mSelectionIndicator);
        }
        if (mRadarIndicator.getParent() != null) {
            mRadarIndicator.getParent().detachChild(mRadarIndicator);
        }

        if (mSelectedStarEntity != null) {
            mSelectionIndicator.setSelectedEntity(mSelectedStarEntity);
            mSelectedStarEntity.attachChild(mSelectionIndicator);

            // if the selected star has a radar, pick the one with the biggest radius to display
            float radarRadius = mSelectedStarEntity.getStar().getRadarRange(EmpireManager.i.getEmpire().getKey());
            if (radarRadius > 0.0f) {
                mSelectedStarEntity.attachChild(mRadarIndicator);
                mRadarIndicator.setScale(radarRadius * Sector.PIXELS_PER_PARSEC * 2.0f);
            }
        }
        if (mSelectedFleetEntity != null) {
            mSelectionIndicator.setSelectedEntity(mSelectedFleetEntity);
            mSelectedFleetEntity.attachChild(mSelectionIndicator);
        }
    }

    /**
     * When a star is updated, if it's one of ours, then we'll want to redraw to make sure we
     * have the latest data (e.g. it might've been renamed)
     */
    @Override
    public void onStarFetched(Star s) {
        // if it's the selected star, we'll want to update the selection
        if (s != null && mSelectedStarEntity != null && s.getKey().equals(mSelectedStarEntity.getStar().getKey())) {
            mSelectedStarEntity.setStar(s);
            refreshSelectionIndicator();
        }
    }

    /** Represents the PointCloud used by the tactical view. */
    private class TacticalPointCloud extends PointCloud {
        public TacticalPointCloud(ArrayList<Vector2> points) {
            super(points);
        }
    }

    /** Represents the ControlField used by the tactical view. */
    private class TacticalControlField extends ControlField {
        private ArrayList<Mesh> mMeshes;

        public TacticalControlField(PointCloud pointCloud, Voronoi voronoi) {
            super(pointCloud, voronoi);
            mMeshes = new ArrayList<Mesh>();
        }

        public void updateAlpha(boolean visible, float alpha) {
            for (Mesh mesh : mMeshes) {
                mesh.setVisible(visible);
                mesh.setAlpha(alpha);
            }
        }

        public void addToScene(Scene scene, VertexBufferObjectManager vboManager, int colour) {
            for (Vector2 pt : mOwnedPoints) {
                log.debug("Adding point: "+pt.x+","+pt.y);
                List<Triangle> triangles = mVoronoi.getTrianglesForPoint(pt);
                if (triangles == null) {
                    continue;
                }

                float[] meshVertices = new float[(triangles.size() + 2) * Mesh.VERTEX_SIZE];
                meshVertices[Mesh.VERTEX_INDEX_X] = (float) pt.x * Sector.SECTOR_SIZE;
                meshVertices[Mesh.VERTEX_INDEX_Y] = (float) pt.y * Sector.SECTOR_SIZE;
                for (int i = 0; i < triangles.size(); i++) {
                    meshVertices[(i + 1) * Mesh.VERTEX_SIZE + Mesh.VERTEX_INDEX_X] = (float) triangles.get(i).centre.x * Sector.SECTOR_SIZE;
                    meshVertices[(i + 1) * Mesh.VERTEX_SIZE + Mesh.VERTEX_INDEX_Y] = (float) triangles.get(i).centre.y * Sector.SECTOR_SIZE;
                }
                meshVertices[(triangles.size() + 1) * Mesh.VERTEX_SIZE + Mesh.VERTEX_INDEX_X] = (float) triangles.get(0).centre.x * Sector.SECTOR_SIZE;
                meshVertices[(triangles.size() + 1) * Mesh.VERTEX_SIZE + Mesh.VERTEX_INDEX_Y] = (float) triangles.get(0).centre.y * Sector.SECTOR_SIZE;

                Mesh mesh = new Mesh(0.0f, 0.0f, meshVertices, triangles.size() + 2, DrawMode.TRIANGLE_FAN, vboManager);
                mesh.setColor(colour);
                mesh.setAlpha(mTacticalZoomAlpha);
                mesh.setVisible(mIsTacticalVisible);
                scene.attachChild(mesh);
                mMeshes.add(mesh);
            }
        }
    }

    /** Represents the Voronoi diagram of the tactical view. */
    private class TacticalVoronoi extends Voronoi {
        public TacticalVoronoi(PointCloud pc) {
            super(pc);
        }
    }

    public interface OnSelectionChangedListener {
        public abstract void onStarSelected(Star star);
        public abstract void onFleetSelected(Fleet fleet);
    }
}
