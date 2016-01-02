package Roguelike.Screens;

import Roguelike.Ability.AbilityTree;
import Roguelike.Ability.ActiveAbility.ActiveAbility;
import Roguelike.Ability.IAbility;
import Roguelike.AssetManager;
import Roguelike.Entity.Entity;
import Roguelike.Entity.EnvironmentEntity;
import Roguelike.Entity.EnvironmentEntity.ActivationAction;
import Roguelike.Entity.GameEntity;
import Roguelike.Entity.Tasks.TaskUseAbility;
import Roguelike.Entity.Tasks.TaskWait;
import Roguelike.Fields.Field;
import Roguelike.Fields.Field.FieldLayer;
import Roguelike.Global;
import Roguelike.Global.Direction;
import Roguelike.Global.Statistic;
import Roguelike.Items.Item;
import Roguelike.RoguelikeGame.ScreenEnum;
import Roguelike.Sprite.Sprite;
import Roguelike.Sprite.SpriteAnimation.MoveAnimation;
import Roguelike.Sprite.SpriteAnimation.StretchAnimation;
import Roguelike.Sprite.SpriteEffect;
import Roguelike.Sprite.TilingSprite;
import Roguelike.Tiles.GameTile;
import Roguelike.Tiles.Point;
import Roguelike.UI.*;
import Roguelike.UI.Tooltip;
import Roguelike.Util.EnumBitflag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.input.GestureDetector.GestureListener;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import javax.tools.Tool;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.PriorityQueue;

public class GameScreen implements Screen, InputProcessor, GestureListener
{
	// ####################################################################//
	// region Constructor

	// ----------------------------------------------------------------------
	public GameScreen()
	{
		Instance = this;
	}

	// endregion Constructor
	// ####################################################################//
	// region Create

	// ----------------------------------------------------------------------
	@Override
	public void show()
	{
		if ( !created )
		{
			create();
			created = true;
		}

		Gdx.input.setInputProcessor( inputMultiplexer );

		resize( Global.ScreenSize[ 0 ], Global.ScreenSize[ 1 ] );
	}

	// ----------------------------------------------------------------------
	private void create()
	{
		batch = new SpriteBatch();

		font = AssetManager.loadFont( "Sprites/GUI/stan0755.ttf", 12, new Color( 1f, 0.9f, 0.8f, 1 ), 1, Color.BLACK, false );
		hightlightfont = AssetManager.loadFont( "Sprites/GUI/stan0755.ttf", 12, new Color( 1f, 1f, 0.9f, 1 ), 1, Color.BLACK, false );

		blank = AssetManager.loadTextureRegion( "Sprites/blank.png" );
		white = AssetManager.loadTextureRegion( "Sprites/white.png" );
		bag = AssetManager.loadSprite( "Oryx/uf_split/uf_items/satchel" );
		bag.drawActualSize = true;
		border = AssetManager.loadSprite( "GUI/frame" );
		speechBubbleArrow = AssetManager.loadTextureRegion( "Sprites/GUI/SpeechBubbleArrow.png" );
		speechBubbleBackground = new NinePatch( AssetManager.loadTextureRegion( "Sprites/GUI/SpeechBubble.png" ), 10, 10, 10, 10 );
		fogSprite = new TilingSprite( "fog", "Masks/fog", "Masks/fog" );

		gestureDetector = new GestureDetector( this );
		gestureDetector.setLongPressSeconds( 0.5f );

		inputMultiplexer = new InputMultiplexer();

		LoadUI();

		InputProcessor inputProcessorOne = this;
		InputProcessor inputProcessorTwo = stage;

		inputMultiplexer.addProcessor( gestureDetector );
		inputMultiplexer.addProcessor( inputProcessorTwo );
		inputMultiplexer.addProcessor( inputProcessorOne );
	}

	// ----------------------------------------------------------------------
	private void LoadUI()
	{
		skin = Global.loadSkin();

		Global.skin = skin;

		stage = new Stage( new ScreenViewport() );

		abilityPanel = new AbilityPanel( skin, stage );
		equipmentPanel = new EquipmentPanel( skin, stage );

		stage.addActor( abilityPanel );
		stage.addActor( equipmentPanel );
		stage.addActor( abilityPanel );

		if (Global.ANDROID)
		{
			Button examineButton = new Button( skin, "examine" );
			examineButton.addListener( new InputListener()
			{
				@Override
				public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
				{
					return true;
				}

				@Override
				public void touchUp (InputEvent event, float x, float y, int pointer, int button)
				{
					examineMode = !examineMode;
				}
			} );


			examineButton.setPosition( 20, 20 );
			stage.addActor( examineButton );
		}

		sheathButton = new Button( skin, "sheath" );
		sheathButton.addListener( new ChangeListener()
		{
			@Override
			public void changed( ChangeEvent event, Actor actor )
			{
				if (!examineMode)
				{
					Global.CurrentLevel.player.weaponSheathed = sheathButton.isChecked();
				}
				else
				{
					sheathButton.setChecked( Global.CurrentLevel.player.weaponSheathed );
				}
			}
		} );
		sheathButton.addListener( new InputListener()
		{
			private Tooltip tooltip;

			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button)
			{
				return mouseMoved( event, x, y );
			}

			@Override
			public boolean mouseMoved(InputEvent event, float x, float y)
			{
				if (!Global.ANDROID || examineMode)
				{
					String text = "";

					if ( sheathButton.isChecked() )
					{
						text = "Enable auto-attack";
					}
					else
					{
						text = "Disable auto-attack";
					}

					Label label = new Label( text, skin );
					Table table = new Table();
					table.add( label ).expand().fill();

					tooltip = new Tooltip( table, skin, stage );
					tooltip.show( event, x, y, false );

					return true;
				}

				return false;
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor)
			{
				if (!Global.ANDROID)
				{
					tooltip.setVisible(false);
					tooltip.remove();
					tooltip.openTooltip = null;
				}
			}

		} );

		sheathButton.setPosition( Global.Resolution[0] - sheathButton.getWidth() - 20, 20 );
		stage.addActor( sheathButton );

		relayoutUI();
	}

	// ----------------------------------------------------------------------
	public void relayoutUI()
	{
		abilityPanel.setX( stage.getWidth() / 2 - abilityPanel.getMinWidth() / 2 );
		abilityPanel.setY( 5 );
		abilityPanel.setWidth( abilityPanel.getMinWidth() );
		abilityPanel.setHeight( abilityPanel.getMinHeight() );

		equipmentPanel.setX( stage.getWidth() / 2 - abilityPanel.getMinWidth() / 2 );
		equipmentPanel.setY( 10 + abilityPanel.getHeight() );
		equipmentPanel.setWidth( equipmentPanel.getMinWidth() );
		equipmentPanel.setHeight( equipmentPanel.getMinHeight() );

		if (contextMenu != null)
		{
			boolean lock = lockContextMenu;
			lockContextMenu = false;

			contextMenu.remove();
			displayContextMenu( contextMenu.Content, lock );
		}
	}

	// endregion Create
	// ####################################################################//
	// region InputProcessor

	// ----------------------------------------------------------------------
	@Override
	public void render( float delta )
	{
		frametime = ( frametime + delta ) / 2.0f;
		fpsAccumulator += delta;
		if ( fpsAccumulator > 0.5f )
		{
			storedFrametime = frametime;
			fps = (int) ( 1.0f / frametime );
			fpsAccumulator = 0;
		}

		if ( !examineMode )
		{
			Global.CurrentLevel.update( delta );
			processPickupQueue();

			sheathButton.setChecked( Global.CurrentLevel.player.weaponSheathed );

			if (contextMenu == null)
			{
				for (AbilityTree tree : Global.CurrentLevel.player.slottedAbilities)
				{
					if (tree != null && tree.current.level == 10 && tree.current.branch1 != null)
					{
						tree.current.mutate( skin, Global.CurrentLevel.player, stage );
						break;
					}
				}
			}
		}

		int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
		int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

		if ( Global.CurrentLevel.player.sprite.spriteAnimation instanceof MoveAnimation )
		{
			int[] offset = Global.CurrentLevel.player.sprite.spriteAnimation.getRenderOffset();

			offsetx -= offset[ 0 ];
			offsety -= offset[ 1 ];
		}

		// do screen shake
		if ( screenShakeRadius > 2 )
		{
			screenShakeAccumulator += delta;
			while ( screenShakeAccumulator >= ScreenShakeSpeed )
			{
				screenShakeAccumulator -= ScreenShakeSpeed;
				screenShakeAngle += ( 150 + MathUtils.random() * 60 );
				screenShakeRadius *= 0.9f;
			}

			offsetx += Math.sin( screenShakeAngle ) * screenShakeRadius;
			offsety += Math.cos( screenShakeAngle ) * screenShakeRadius;
		}

		int mousex = ( mousePosX - offsetx ) / Global.TileSize;
		int mousey = ( mousePosY - offsety ) / Global.TileSize;

		int tileSize3 = Global.TileSize / 3;

		Gdx.gl.glClearColor( 0, 0, 0, 1 );
		Gdx.gl.glClear( GL20.GL_COLOR_BUFFER_BIT );

		if ( examineMode )
		{
			batch.setShader( GrayscaleShader.Instance );
		}

		batch.begin();

		hasStatus.clear();
		entitiesWithSpeech.clear();

		renderBackground( offsetx, offsety );

		renderVisibleTiles( offsetx, offsety, tileSize3 );
		if ( Global.CurrentDialogue == null )
		{
			renderCursor( offsetx, offsety, mousex, mousey, delta );
		}
		renderActiveAbilities( offsetx, offsety );
		renderSpriteEffects( offsetx, offsety, tileSize3 );

		flush( batch );

		if ( examineMode )
		{
			batch.setShader( null );
		}

		renderStatus( offsetx, offsety );

		renderSpeechBubbles( offsetx, offsety, delta );

		if ( Global.CurrentDialogue == null )
		{
			if ( preparedAbility != null )
			{
				batch.setColor( 0.3f, 0.6f, 0.8f, 0.5f );
				for ( Point tile : abilityTiles )
				{
					batch.draw( white, tile.x * Global.TileSize + offsetx, tile.y * Global.TileSize + offsety, Global.TileSize, Global.TileSize );
				}
			}

			// if ( Global.ANDROID )
			// {
			// EntityStatusRenderer.draw( Global.CurrentLevel.player, batch,
			// Global.Resolution[0] - ( Global.Resolution[0] / 4 ) - 120,
			// Global.Resolution[1] - 120, Global.Resolution[0] / 4, 100, 1.0f /
			// 4.0f );
			// }
			// else
			// {
			// font.draw( batch, Global.PlayerName + " the " +
			// Global.PlayerTitle, 20, Global.Resolution[1] - 20 );
			// font.draw( batch, "Essence: " +
			// Global.CurrentLevel.player.essence, 20, Global.Resolution[1] - 40
			// );
			// EntityStatusRenderer.draw( Global.CurrentLevel.player, batch, 20,
			// Global.Resolution[1] - 160, Global.Resolution[0] / 4, 100, 1.0f /
			// 4.0f );
			// }

			batch.end();

			stage.act( delta );
			stage.draw();

			batch.begin();
		}

		if ( dragDropPayload != null && dragDropPayload.shouldDraw() )
		{
			dragDropPayload.sprite.render( batch, (int) dragDropPayload.x, (int) dragDropPayload.y, 32, 32 );
		}

		font.draw( batch, "FPS: " + fps, Global.Resolution[ 0 ] - 100, Global.Resolution[ 1 ] - 20 );
		font.draw( batch, "Frametime: " + storedFrametime, Global.Resolution[ 0 ] - 200, Global.Resolution[ 1 ] - 40 );

		batch.end();

		// limit fps
		sleep( Global.FPS );
	}

	// ----------------------------------------------------------------------
	@Override
	public void resize( int width, int height )
	{
		Global.ScreenSize[ 0 ] = width;
		Global.ScreenSize[ 1 ] = height;

		float w = Global.TargetResolution[ 0 ];
		float h = Global.TargetResolution[ 1 ];

		if ( width < height )
		{
			h = w * ( (float) height / (float) width );
		}
		else
		{
			w = h * ( (float) width / (float) height );
		}

		Global.Resolution[ 0 ] = (int) w;
		Global.Resolution[ 1 ] = (int) h;

		camera = new OrthographicCamera( Global.Resolution[ 0 ], Global.Resolution[ 1 ] );
		camera.translate( Global.Resolution[ 0 ] / 2, Global.Resolution[ 1 ] / 2 );
		camera.setToOrtho( false, Global.Resolution[ 0 ], Global.Resolution[ 1 ] );
		camera.update();

		batch.setProjectionMatrix( camera.combined );
		stage.getViewport().setCamera( camera );
		stage.getViewport().setWorldWidth( Global.Resolution[ 0 ] );
		stage.getViewport().setWorldHeight( Global.Resolution[ 1 ] );
		stage.getViewport().setScreenWidth( Global.ScreenSize[ 0 ] );
		stage.getViewport().setScreenHeight( Global.ScreenSize[ 1 ] );

		relayoutUI();
	}

	// ----------------------------------------------------------------------
	@Override
	public void pause()
	{
		Global.save();
	}

	// ----------------------------------------------------------------------
	@Override
	public void resume()
	{
	}

	// ----------------------------------------------------------------------
	@Override
	public void hide()
	{
	}

	// ----------------------------------------------------------------------
	@Override
	public void dispose()
	{
	}

	// ----------------------------------------------------------------------
	private void renderBackground( int offsetx, int offsety )
	{
		if ( Global.CurrentLevel.background != null )
		{
			Sprite sprite = Global.CurrentLevel.background;

			temp.set( Global.CurrentLevel.Ambient ).mul( Global.DayNightFactor );
			temp.a = 1;

			batch.setColor( temp );

			if ( Global.CurrentLevel.isVisionRestricted )
			{
				for ( Point pos : Global.CurrentLevel.visibilityData.getCurrentShadowCast() )
				{
					if ( pos.x < 0 || pos.y < 0 || pos.x >= Global.CurrentLevel.width || pos.y >= Global.CurrentLevel.height )
					{
						int x = pos.x;
						int y = pos.y;

						int cx = x * Global.TileSize + offsetx;
						int cy = y * Global.TileSize + offsety;

						sprite.render( batch, cx, cy, Global.TileSize, Global.TileSize );
					}
				}
			}
			else
			{
				int px = Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize + offsetx;
				int py = Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize + offsety;

				int sx = px - ( (int) roundTo( px, Global.TileSize ) ) - Global.TileSize;
				int sy = py - ( (int) roundTo( py, Global.TileSize ) ) - Global.TileSize;

				for ( int cx = sx; cx < Global.Resolution[ 0 ]; cx += Global.TileSize )
				{
					for ( int cy = sy; cy < Global.Resolution[ 1 ]; cy += Global.TileSize )
					{
						sprite.render( batch, cx, cy, Global.TileSize, Global.TileSize );
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private void renderVisibleTiles( int offsetx, int offsety, int tileSize3 )
	{
		for ( int x = -1; x < Global.CurrentLevel.width+1; x++ )
		{
			int drawX = x * Global.TileSize + offsetx;
			if (drawX + Global.TileSize < 0 || drawX > Global.Resolution[0])
			{
				continue;
			}

			for ( int y = -1; y < Global.CurrentLevel.height+1; y++ )
			{
				int drawY = y * Global.TileSize + offsety;
				if (drawY + Global.TileSize < 0 || drawY > Global.Resolution[1])
				{
					continue;
				}

				GameTile gtile = Global.CurrentLevel.getGameTile( x, y );
				if (gtile != null)
				{

					// skip if not visible
					if (gtile.unseenBitflag.getBitFlag() == 0)
					{
						// if below was visible, then draw
						GameTile btile = Global.CurrentLevel.getGameTile( x, y-1 );
						if (btile != null && btile.unseenBitflag.getBitFlag() != 0)
						{
							queueSprite( fogSprite.getSprite( gtile.unseenBitflag ), unseenFogCol, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.UNSEENFOG );
						}

						continue;
					}

					for ( int i = 0; i < gtile.getSprites().size; i++ )
					{
						Sprite sprite = gtile.getSprites().get(i);
						queueSprite( sprite, gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, sprite.drawActualSize ? RenderLayer.RAISEDENTITY : RenderLayer.GROUNDTILE, i );
					}

					GameTile nextTile = Global.CurrentLevel.getGameTile( x, y - 1 );
					if ( nextTile != null && nextTile.getTilingSprite() != null )
					{
						GameTile ogtile = nextTile;
						GameTile oprevTile = gtile;

						if ( ogtile.getTilingSprite().overhangSprite != null
							 && oprevTile != null
							 && ( oprevTile.getTilingSprite() == null || !oprevTile.getTilingSprite().name.equals( gtile.getTilingSprite().name ) ) )
						{
							queueSprite( ogtile.getTilingSprite().overhangSprite, oprevTile.light, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.OVERHANG );
						}
					}

					if ( gtile.getTilingSprite() != null )
					{
						Global.CurrentLevel.buildTilingBitflag(directionBitflag, x, y, gtile.getTilingSprite().name);
						Sprite sprite = gtile.getTilingSprite().getSprite( directionBitflag );
						queueSprite( sprite, gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, sprite.drawActualSize ? RenderLayer.RAISEDENTITY : RenderLayer.GROUNDTILE, gtile.getSprites().size );
					}

					if ( gtile.hasFields )
					{
						for ( FieldLayer layer : FieldLayer.values() )
						{
							Field field = gtile.fields.get( layer );
							if ( field != null )
							{
								if ( field.layer == FieldLayer.GROUND )
								{
									queueSprite( field.sprite, gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.GROUNDFIELD );
								}
								else
								{
									queueSprite( field.sprite, gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.OVERHEADFIELD );
								}
							}
						}
					}

					if ( gtile.environmentEntity != null && gtile.environmentEntity.tile[ 0 ][ 0 ] == gtile )
					{
						EnvironmentEntity entity = gtile.environmentEntity;

						int cx = x * Global.TileSize + offsetx;
						int cy = y * Global.TileSize + offsety;

						int width = Global.TileSize;
						int height = Global.TileSize;

						Sprite sprite = entity.sprite;

						if ( entity.tilingSprite != null )
						{
							Global.CurrentLevel.buildTilingBitflag(directionBitflag, x, y, entity.tilingSprite.name);
							sprite = entity.tilingSprite.getSprite( directionBitflag );
						}

						if ( entity.location != Direction.CENTER )
						{
							if ( entity.location == Direction.EAST || entity.location == Direction.WEST )
							{
								cx += -entity.location.getX() * ( Global.TileSize / 2 );
							}
							else if ( entity.location == Direction.SOUTH )
							{
								cy += Global.TileSize;
							}
						}

						if ( entity.canTakeDamage && entity.HP < entity.statistics.get( Statistic.CONSTITUTION ) * 10 || entity.stacks.size > 0 )
						{
							hasStatus.add( entity );
						}

						if ( entity.overHead )
						{
							queueSprite( sprite, gtile.light, cx, cy, width, height, RenderLayer.OVERHEADENTITY );
						}
						else if ( sprite.drawActualSize )
						{
							queueSprite( sprite, gtile.light, cx, cy, width, height, RenderLayer.RAISEDENTITY );
						}
						else
						{
							queueSprite( sprite, gtile.light, cx, cy, width, height, RenderLayer.GROUNDENTITY );
						}

						if ( entity.tile[ 0 ][ 0 ].visible && entity.popup != null )
						{
							entitiesWithSpeech.add( entity );
						}
					}

					if ( gtile.visible || ( gtile.seen && gtile.seenBitflag.getBitFlag() != 0 ) )
					{
						GameEntity entity = gtile.entity;

						if ( entity != null && entity.tile[ 0 ][ 0 ] == gtile )
						{
							int cx = x * Global.TileSize + offsetx;
							int cy = y * Global.TileSize + offsety;

							int width = Global.TileSize;
							int height = Global.TileSize;

							Sprite sprite = entity.sprite;

							if ( entity.tilingSprite != null )
							{
								Global.CurrentLevel.buildTilingBitflag(directionBitflag, x, y, gtile.getTilingSprite().name);
								sprite = gtile.getTilingSprite().getSprite( directionBitflag );
							}

							if ( entity.location != Direction.CENTER )
							{
								Direction dir = entity.location;
								cx = cx + tileSize3 * ( dir.getX() * -1 + 1 );
								cy = cy + tileSize3 * ( dir.getY() * -1 + 1 );
								width = tileSize3;
								height = tileSize3;
							}

							if ( entity.canTakeDamage && entity.HP < entity.statistics.get( Statistic.CONSTITUTION ) * 10 || entity.stacks.size > 0 )
							{
								hasStatus.add( entity );
							}

							if ( sprite.drawActualSize )
							{
								queueSprite( sprite, gtile.light, cx, cy, width, height, RenderLayer.RAISEDENTITY );
							}
							else
							{
								queueSprite( sprite, gtile.light, cx, cy, width, height, RenderLayer.GROUNDENTITY );
							}

							if ( entity.tile[ 0 ][ 0 ].visible && entity.popup != null )
							{
								entitiesWithSpeech.add( entity );
							}
						}

						if ( gtile.items.size > 0 )
						{
							if ( gtile.items.size == 1 )
							{
								queueSprite( gtile.items.get( 0 ).getIcon(), gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.ITEM );
							}
							else
							{
								queueSprite( bag, gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.ITEM );

								for ( Item item : gtile.items )
								{
									if ( item.getIcon().spriteAnimation != null )
									{
										queueSprite( item.getIcon(), gtile.light, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.ITEM );
									}
								}
							}
						}

						if ( gtile.orbs.size > 0 && gtile.spriteEffects.size == 0 )
						{
							int index = 0;
							for ( GameTile.OrbType type : GameTile.OrbType.values() )
							{
								if ( gtile.orbs.containsKey( type ) )
								{
									int val = gtile.orbs.get( type );

									int cx = x * Global.TileSize + offsetx;
									int cy = y * Global.TileSize + offsety;

									float scale = 0.5f + 0.5f * ( MathUtils.clamp( val, 10.0f, 1000.0f ) / 1000.0f );

									float size = Global.TileSize * scale;
									cx = (int) (( cx + Global.TileSize / 2 ) - size / 2);
									cy = (int) (( cy + Global.TileSize / 2 ) - size / 2);

									Direction dir = Direction.values()[index++];

									cx += dir.getX() * (size/2);
									cy += dir.getY() * (size/2);

									Sprite sprite = orbs.get( type );
									if (sprite == null)
									{
										sprite = AssetManager.loadSprite( type.spriteName );
										orbs.put( type, sprite );
									}

									queueSprite( sprite, gtile.light, cx, cy, (int) size, (int) size, RenderLayer.ESSENCE );
								}
							}
						}
					}
				}

				if (gtile == null)
				{
					Global.CurrentLevel.buildTilingBitflag( directionBitflag, x, y, "seen" );
					//if (directionBitflag.getBitFlag() != 0)
					{
						Sprite sprite = fogSprite.getSprite( directionBitflag );
						queueSprite( sprite, seenFogCol, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.SEENFOG );
					}

					Global.CurrentLevel.buildTilingBitflag( directionBitflag, x, y, "unseen" );
					//if (directionBitflag.getBitFlag() != 0)
					{
						Sprite sprite = fogSprite.getSprite( directionBitflag );
						queueSprite( sprite, unseenFogCol, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.UNSEENFOG );
					}
				}
				else if (!gtile.visible)
				{
					// not visible, so draw fog
					Sprite sprite = fogSprite.getSprite( gtile.seenBitflag );
					queueSprite( sprite, seenFogCol, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.SEENFOG );

					if (!gtile.seen)
					{
						sprite = fogSprite.getSprite( gtile.unseenBitflag );
						queueSprite( sprite, unseenFogCol, drawX, drawY, Global.TileSize, Global.TileSize, RenderLayer.UNSEENFOG );
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private void renderCursor( int offsetx, int offsety, int mousex, int mousey, float delta )
	{
		if ( !mouseOverUI && !Global.ANDROID )
		{
			Color colour = Color.GREEN;

			if ( mousex < 0
				 || mousex >= Global.CurrentLevel.width
				 || mousey < 0
				 || mousey >= Global.CurrentLevel.height
				 || !Global.CurrentLevel.getGameTile( mousex, mousey ).seen )
			{
				colour = Color.RED;
			}
			else
			{
				GameTile mouseTile = Global.CurrentLevel.getGameTile( mousex, mousey );
				if ( mouseTile.tileData.passableBy.intersect( Global.CurrentLevel.player.getTravelType() ) )
				{
					colour = Color.GREEN;
				}
				else
				{
					colour = Color.RED;
				}
			}

			border.update( delta );

			queueSprite( border, colour, mousex * Global.TileSize + offsetx, mousey * Global.TileSize + offsety, Global.TileSize, Global.TileSize, RenderLayer.CURSOR );
		}
	}

	// ----------------------------------------------------------------------
	private void renderStatus( int offsetx, int offsety )
	{
		batch.setColor( Color.WHITE );

		for ( Entity e : hasStatus )
		{
			if (!e.tile[0][0].visible)
			{
				continue;
			}

			int x = e.tile[ 0 ][ 0 ].x;
			int y = e.tile[ 0 ][ 0 ].y;

			int cx = x * Global.TileSize + offsetx;
			int cy = y * Global.TileSize + offsety;

			if ( e.sprite.spriteAnimation != null )
			{
				int[] offset = e.sprite.spriteAnimation.getRenderOffset();
				cx += offset[ 0 ];
				cy += offset[ 1 ];
			}

			Color colour = null;

			if (e instanceof GameEntity)
			{
				GameEntity ge = (GameEntity)e;

				if (ge.isAllies( Global.CurrentLevel.player ))
				{
					if (ge.getVariableMap().containsKey( "summon" ))
					{
						colour = Color.CYAN;
					}
					else
					{
						colour = Color.GREEN;
					}
				}
				else
				{
					colour = Color.RED;
				}
			}
			else
			{
				colour = Color.CYAN;
			}

			EntityStatusRenderer.draw( e, batch, cx, cy, Global.TileSize, Global.TileSize, 1.0f / 12.0f, colour );
		}
	}

	// ----------------------------------------------------------------------
	private void renderActiveAbilities( int offsetx, int offsety )
	{
		if ( Global.CurrentLevel.ActiveAbilities.size > 0 )
		{
			for ( ActiveAbility aa : Global.CurrentLevel.ActiveAbilities )
			{
				for ( GameTile tile : aa.AffectedTiles )
				{
					if ( tile.visible )
					{
						queueSprite( aa.getSprite(), Color.WHITE, tile.x * Global.TileSize + offsetx, tile.y * Global.TileSize + offsety, Global.TileSize, Global.TileSize, RenderLayer.ABILITY );
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private void renderSpriteEffects( int offsetx, int offsety, int tileSize3 )
	{
		for ( int x = 0; x < Global.CurrentLevel.width; x++ )
		{
			for ( int y = 0; y < Global.CurrentLevel.height; y++ )
			{
				GameTile gtile = Global.CurrentLevel.Grid[ x ][ y ];

				if ( gtile.visible && gtile.spriteEffects.size > 0 )
				{
					for ( SpriteEffect e : gtile.spriteEffects )
					{
						if ( e.Corner == Direction.CENTER )
						{
							queueSprite( e.Sprite, Color.WHITE, x * Global.TileSize + offsetx, y * Global.TileSize + offsety, Global.TileSize, Global.TileSize, RenderLayer.EFFECT );
						}
						else
						{
							queueSprite( e.Sprite, Color.WHITE, x * Global.TileSize + offsetx + tileSize3 * ( e.Corner.getX() * -1 + 1 ), y
																																		  * Global.TileSize
																																		  + offsety
																																		  + tileSize3
																																			* ( e.Corner.getY() * -1 + 1 ), tileSize3, tileSize3, RenderLayer.EFFECT );
						}
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private void renderSpeechBubbles( int offsetx, int offsety, float delta )
	{
		for ( Entity entity : entitiesWithSpeech )
		{
			if ( entity.popupDuration <= 0 && entity.displayedPopup.length() == entity.popup.length() )
			{
				entity.popupFade -= delta;

				if ( entity.popupFade <= 0 )
				{
					entity.popup = null;
					continue;
				}
			}

			float alpha = 1;
			if ( entity.popupDuration <= 0 && entity.displayedPopup.length() == entity.popup.length() )
			{
				alpha *= entity.popupFade;

				if ( alpha < 0 )
				{
					alpha = 0;
				}
			}
			tempColour.set( 1, 1, 1, alpha );

			int x = entity.tile[ 0 ][ 0 ].x;
			int y = entity.tile[ 0 ][ 0 ].y;

			y += 1;

			int cx = x * Global.TileSize + offsetx + Global.TileSize / 2;
			int cy = y * Global.TileSize + offsety;

			if ( entity.sprite.spriteAnimation != null )
			{
				int[] offset = entity.sprite.spriteAnimation.getRenderOffset();
				cx += offset[ 0 ];
				cy += offset[ 1 ];
			}

			layout.setText( font, entity.popup, tempColour, ( stage.getWidth() / 3 ) * 2, Align.left, true );

			float left = cx - ( layout.width / 2 ) - 10;

			if ( left < 0 )
			{
				left = 0;
			}

			float right = left + layout.width + 20;

			if ( right >= stage.getWidth() )
			{
				left -= right - stage.getWidth();
			}

			float width = layout.width;
			float height = layout.height;

			layout.setText( font, entity.displayedPopup, tempColour, ( stage.getWidth() / 3 ) * 2, Align.left, true );

			batch.setColor( tempColour );

			speechBubbleBackground.draw( batch, left, cy, width + 20, height + 20 );
			batch.draw( speechBubbleArrow, cx - 4, cy - 6, 8, 8 );

			font.draw( batch, layout, left + 10, cy + layout.height + 10 );
		}

		if ( Global.CurrentDialogue != null && Global.CurrentDialogue.currentInput != null )
		{
			int padding = Global.ANDROID ? 20 : 10;

			int x = Global.CurrentDialogue.entity.tile[ 0 ][ 0 ].x;
			int y = Global.CurrentDialogue.entity.tile[ 0 ][ 0 ].y;

			int cx = x * Global.TileSize + offsetx + Global.TileSize / 2;
			int cy = y * Global.TileSize + offsety;

			float layoutwidth = 0;
			float layoutheight = 0;
			for ( int i = 0; i < Global.CurrentDialogue.currentInput.choices.size; i++ )
			{
				String message = ( i + 1 ) + ": " + Global.expandNames( Global.CurrentDialogue.currentInput.choices.get( i ) );

				layout.setText( font, message, tempColour, ( stage.getWidth() / 3 ) * 2, Align.left, true );
				if ( layout.width > layoutwidth )
				{
					layoutwidth = layout.width;
				}
				layoutheight += layout.height + padding;
			}

			cy -= layoutheight + 20;

			float left = cx - ( layoutwidth / 2 ) - 10;

			if ( left < 0 )
			{
				left = 0;
			}

			float right = left + layoutwidth + 20;

			if ( right >= stage.getWidth() )
			{
				left -= right - stage.getWidth();
			}

			speechBubbleBackground.draw( batch, left, cy, layoutwidth + 20, layoutheight + 20 );

			float voffset = padding / 2;
			for ( int i = Global.CurrentDialogue.currentInput.choices.size - 1; i >= 0; i-- )
			{
				String message = ( i + 1 ) + ": " + Global.expandNames( Global.CurrentDialogue.currentInput.choices.get( i ) );

				BitmapFont font = this.font;
				if ( Global.CurrentDialogue.mouseOverInput == i )
				{
					font = hightlightfont;
				}

				layout.setText( font, message, tempColour, ( stage.getWidth() / 3 ) * 2, Align.left, true );

				font.draw( batch, layout, left + 10, cy + layout.height + 10 + voffset );

				voffset += layout.height + padding;
			}
		}
	}

	// ----------------------------------------------------------------------
	private void queueSprite( Sprite sprite, Color colour, int x, int y, int width, int height, RenderLayer layer )
	{
		queueSprite( sprite, colour, x, y, width, height, layer, 0 );
	}

	// ----------------------------------------------------------------------
	private void queueSprite( Sprite sprite, Color colour, int x, int y, int width, int height, RenderLayer layer, int index )
	{
		if ( sprite != null && sprite.spriteAnimation != null )
		{
			int[] offset = sprite.spriteAnimation.getRenderOffset();
			x += offset[ 0 ];
			y += offset[ 1 ];
		}

		if ( x + width < 0 || y + height < 0 || x > Global.Resolution[ 0 ] || y > Global.Resolution[ 1 ] ) { return; }

		queuedSprites.add( renderSpritePool.obtain().set( sprite, colour, x, y, width, height, layer, index ) );
	}

	// ----------------------------------------------------------------------
	private void flush( Batch batch )
	{
		Color col = batch.getColor();
		while (!queuedSprites.isEmpty())
		{
			RenderSprite rs = queuedSprites.poll();

			temp.set( rs.colour );
			if ( !temp.equals( col ) )
			{
				batch.setColor( temp );
				col.set( temp );
			}

			rs.sprite.render( batch, rs.x, rs.y, rs.width, rs.height );
			renderSpritePool.free( rs );
		}
	}

	// ----------------------------------------------------------------------
	private void processPickupQueue()
	{
		if ( contextMenu == null && pickupQueue.size > 0 )
		{
			final Item item = pickupQueue.removeIndex( 0 );

			if ( item.slots.size > 0 )
			{
				// Is Equipment

				Table comparison = item.createTable( skin, Global.CurrentLevel.player );

				Table table = new Table();

				table.add( comparison ).expand().fill();
				table.row();

				table.add( new Seperator( skin, false ) ).expandX().fillX().pad( 10 );
				table.row();

				TextButton equipButton = new TextButton( "Equip", skin );
				equipButton.addListener( new InputListener()
				{

					@Override
					public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
					{
						return true;
					}

					@Override
					public void touchUp( InputEvent event, float x, float y, int pointer, int button )
					{
						lockContextMenu = false;
						clearContextMenu();
						Global.CurrentLevel.player.getInventory().equip( item );
					}
				} );

				TextButton dropButton = new TextButton( "Drop", skin );
				dropButton.addListener( new InputListener()
				{

					@Override
					public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
					{
						return true;
					}

					@Override
					public void touchUp( InputEvent event, float x, float y, int pointer, int button )
					{
						lockContextMenu = false;
						clearContextMenu();
						Global.CurrentLevel.player.tile[ 0 ][ 0 ].items.add( item );
					}
				} );

				Table buttons = new Table();

				buttons.add( equipButton );
				buttons.add( dropButton );

				table.add( buttons );

				table.pack();

				displayContextMenu( table, true );
			}
			else if ( item.ability != null )
			{
				if ( item.ability.current.current instanceof ActiveAbility )
				{
					( (ActiveAbility) item.ability.current.current ).setCaster( Global.CurrentLevel.player );
				}

				// Is ability
				Table table = new Table();

				table.add( item.ability.current.current.createTable( skin, Global.CurrentLevel.player ) ).expand().fill();
				table.row();

				table.add( new Seperator( skin, false ) ).expandX().fillX().pad( 10 );
				table.row();

				TextButton dropButton = new TextButton( "Drop", skin );
				dropButton.addListener( new InputListener()
				{

					@Override
					public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
					{
						return true;
					}

					@Override
					public void touchUp( InputEvent event, float x, float y, int pointer, int button )
					{
						lockContextMenu = false;
						clearContextMenu();
						Global.CurrentLevel.player.tile[ 0 ][ 0 ].items.add( item );
						abilityToEquip = null;
					}
				} );

				table.add( dropButton );

				table.pack();

				displayContextMenu( table, true );

				abilityToEquip = item.ability;
			}
			else
			{
				addActorItemPickupAction( Global.CurrentLevel.player, item );
				Global.CurrentLevel.player.getInventory().addItem( item );
			}
		}
	}

	// endregion InputProcessor
	// ####################################################################//
	// region GestureListener

	// ----------------------------------------------------------------------
	@Override
	public boolean keyDown( int keycode )
	{
		if ( Global.CurrentDialogue != null )
		{
			if ( Global.CurrentDialogue.currentInput != null && keycode >= Keys.NUM_1 && keycode <= Keys.NUM_9 )
			{
				int val = keycode - Keys.NUM_0;
				if ( val <= Global.CurrentDialogue.currentInput.choices.size )
				{
					Global.CurrentDialogue.currentInput.answer = val;
				}
			}

			if ( Global.CurrentDialogue.entity.popup.length() == Global.CurrentDialogue.entity.displayedPopup.length() )
			{
				Global.CurrentDialogue.advance();
			}
			else
			{
				Global.CurrentDialogue.entity.displayedPopup = Global.CurrentDialogue.entity.popup;
			}
		}
		else if ( keycode == Keys.S )
		{
			Global.save();
		}
		else if ( keycode == Keys.L )
		{
			Global.load();
		}
		else if ( keycode == Keys.A )
		{
			AbilityTree tree = new AbilityTree( "FireAttunement" );

			Item item = new Item();
			item.ability = tree;

			GameTile playerTile = Global.CurrentLevel.player.tile[ 0 ][ 0 ];

			playerTile.items.add( item );
		}
		else if ( keycode == Keys.W )
		{
			Field field = Field.load( "Water" );
			field.stacks = 10;
			GameTile playerTile = Global.CurrentLevel.player.tile[ 0 ][ 0 ];
			field.trySpawnInTile( playerTile, 10 );
		}
		else if ( keycode == Keys.F )
		{
			Field field = Field.load( "Fire" );
			field.stacks = 1;
			GameTile playerTile = Global.CurrentLevel.player.tile[ 0 ][ 0 ];
			field.trySpawnInTile( playerTile, 1 );
		}
		else if ( keycode == Keys.G )
		{
			Field field = Field.load( "IceFog" );
			field.stacks = 4;
			GameTile playerTile = Global.CurrentLevel.player.tile[ 0 ][ 0 ];
			field.trySpawnInTile( playerTile, 4 );
		}
		else if ( keycode == Keys.H )
		{
			Field field = Field.load( "Static" );
			GameTile playerTile = Global.CurrentLevel.player.tile[ 0 ][ 0 ];
			GameTile newTile = playerTile.level.getGameTile( playerTile.x + 1, playerTile.y + 1 );
			field.trySpawnInTile( newTile, 10 );
		}
		else if ( keycode == Keys.E )
		{
			examineMode = !examineMode;
		}
		else if ( keycode >= Keys.NUM_1 && keycode <= Keys.NUM_9 )
		{
			int i = keycode - Keys.NUM_1;
			AbilityTree a = Global.CurrentLevel.player.slottedAbilities.get( i );
			if ( a != null && a.current.current instanceof ActiveAbility && ( (ActiveAbility) a.current.current ).isAvailable() )
			{
				prepareAbility( (ActiveAbility) a.current.current );
			}
		}
		else if ( keycode == Keys.ENTER )
		{
			if ( Global.CurrentLevel.player.tile[ 0 ][ 0 ].environmentEntity != null )
			{
				for ( ActivationAction action : Global.CurrentLevel.player.tile[ 0 ][ 0 ].environmentEntity.actions )
				{
					if ( action.visible )
					{
						action.activate( Global.CurrentLevel.player.tile[ 0 ][ 0 ].environmentEntity );
						Global.CurrentLevel.player.tasks.add( new TaskWait() );
						break;
					}
				}
			}
		}
		else if ( keycode == Keys.ESCAPE )
		{
			OptionsScreen.Instance.screen = ScreenEnum.GAME;
			Global.Game.switchScreen( ScreenEnum.OPTIONS );
		}

		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean keyUp( int keycode )
	{
		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean keyTyped( char character )
	{
		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean touchDown( int screenX, int screenY, int pointer, int button )
	{
		addTouchAction( screenX, Global.ScreenSize[1] - screenY );

		if ( Tooltip.openTooltip != null )
		{
			Tooltip.openTooltip.setVisible( false );
			Tooltip.openTooltip.remove();
			Tooltip.openTooltip = null;
		}

		clearContextMenu();
		return true;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean touchUp( int screenX, int screenY, int pointer, int button )
	{
		if ( longPressed || dragged ) { return false; }

		if ( Tooltip.openTooltip != null )
		{
			Tooltip.openTooltip.setVisible( false );
			Tooltip.openTooltip.remove();
			Tooltip.openTooltip = null;
		}

		if (Global.ANDROID && examineMode)
		{
			mouseMoved( screenX, screenY );
		}

		clearContextMenu();

		if ( contextMenu != null || examineMode )
		{
			return true;
		}

		if ( Global.CurrentDialogue != null )
		{
			if ( Global.CurrentDialogue.currentInput != null )
			{
				if ( Global.ANDROID )
				{
					int mouseOver = getMouseOverDialogueOption( screenX, screenY );
					Global.CurrentDialogue.mouseOverInput = mouseOver;
				}

				if ( Global.CurrentDialogue.mouseOverInput != -1 )
				{
					Global.CurrentDialogue.currentInput.answer = Global.CurrentDialogue.mouseOverInput + 1;
					Global.CurrentDialogue.mouseOverInput = -1;
				}
			}

			if ( Global.CurrentDialogue.entity.popup.length() == Global.CurrentDialogue.entity.displayedPopup.length() )
			{
				Global.CurrentDialogue.advance();
			}
			else
			{
				Global.CurrentDialogue.entity.displayedPopup = Global.CurrentDialogue.entity.popup;
			}
		}
		else
		{
			Vector3 mousePos = camera.unproject( new Vector3( screenX, screenY, 0 ) );

			int mousePosX = (int) mousePos.x;
			int mousePosY = (int) mousePos.y;

			int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
			int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

			int x = ( mousePosX - offsetx ) / Global.TileSize;
			int y = ( mousePosY - offsety ) / Global.TileSize;

			if ( preparedAbility != null )
			{
				if ( button == Buttons.LEFT )
				{
					if ( x >= 0 && x < Global.CurrentLevel.width && y >= 0 && y < Global.CurrentLevel.height )
					{
						GameTile tile = Global.CurrentLevel.getGameTile( x, y );
						if ( preparedAbility.isTargetValid( tile, abilityTiles ) )
						{
							Global.CurrentLevel.player.tasks.add( new TaskUseAbility( Global.PointPool.obtain().set( x, y ), preparedAbility ) );
						}
					}
				}
				preparedAbility = null;
			}
			else
			{
				if ( button == Buttons.RIGHT )
				{
					rightClick( screenX, screenY );
				}
				else
				{
					if ( x >= 0 && x < Global.CurrentLevel.width && y >= 0 && y < Global.CurrentLevel.height && Global.CurrentLevel.getGameTile( x, y ).seen )
					{
						Global.CurrentLevel.player.AI.setData( "ClickPos", Global.PointPool.obtain().set( x, y ) );
					}
					else
					{
						x = MathUtils.clamp( x, -1, 1 );
						y = MathUtils.clamp( y, -1, 1 );

						x += Global.CurrentLevel.player.tile[ 0 ][ 0 ].x;
						y += Global.CurrentLevel.player.tile[ 0 ][ 0 ].y;

						Global.CurrentLevel.player.AI.setData( "ClickPos", Global.PointPool.obtain().set( x, y ) );
					}
				}
			}
		}

		return true;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean touchDragged( int screenX, int screenY, int pointer )
	{
		if ( dragDropPayload != null )
		{
			dragDropPayload.x = screenX - 16;
			dragDropPayload.y = Global.Resolution[ 1 ] - screenY - 16;
		}

		if ( Math.abs( screenX - startX ) > 10 || Math.abs( screenY - startY ) > 10 )
		{
			dragged = true;
		}

		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean mouseMoved( int screenX, int screenY )
	{
		if ( Tooltip.openTooltip != null )
		{
			Tooltip.openTooltip.setVisible( false );
			Tooltip.openTooltip.remove();
			Tooltip.openTooltip = null;
		}

		if ( Global.CurrentDialogue != null )
		{
			if ( Global.CurrentDialogue.currentInput != null )
			{
				int mouseOver = getMouseOverDialogueOption( screenX, screenY );
				Global.CurrentDialogue.mouseOverInput = mouseOver;
			}
		}
		else
		{
			Vector3 mousePos = camera.unproject( new Vector3( screenX, screenY, 0 ) );

			mousePosX = (int) mousePos.x;
			mousePosY = (int) mousePos.y;

			stage.setScrollFocus( null );

			int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
			int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

			mouseOverUI = false;

			int x = ( mousePosX - offsetx ) / Global.TileSize;
			int y = ( mousePosY - offsety ) / Global.TileSize;

			if ( x >= 0 && x < Global.CurrentLevel.width && y >= 0 && y < Global.CurrentLevel.height )
			{
				GameTile tile = Global.CurrentLevel.getGameTile( x, y );

				if ( tile.entity != null )
				{
					Table table = EntityStatusRenderer.getMouseOverTable( tile.entity, x * Global.TileSize + offsetx, y * Global.TileSize + offsety, Global.TileSize, Global.TileSize, 1.0f / 8.0f, mousePosX, mousePosY, skin );

					if ( table != null )
					{
						Tooltip tooltip = new Tooltip( table, skin, stage );
						tooltip.show( mousePosX, mousePosY, false );
					}
				}
			}

			{
				Table table = EntityStatusRenderer.getMouseOverTable( Global.CurrentLevel.player, 20, Global.Resolution[ 1 ] - 120, Global.Resolution[ 0 ] / 4, 100, 1.0f / 4.0f, mousePosX, mousePosY, skin );

				if ( table != null )
				{
					Tooltip tooltip = new Tooltip( table, skin, stage );
					tooltip.show( mousePosX, mousePosY, false );
				}
			}
		}

		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean scrolled( int amount )
	{
		if ( !mouseOverUI )
		{
			Global.TileSize -= amount * 5;
			if ( Global.TileSize < 2 )
			{
				Global.TileSize = 2;
			}
		}

		return false;
	}

	// ----------------------------------------------------------------------
	private int getMouseOverDialogueOption( float screenX, float screenY )
	{
		int padding = Global.ANDROID ? 20 : 10;

		Vector3 mousePos = camera.unproject( new Vector3( screenX, screenY, 0 ) );

		mousePosX = (int) mousePos.x;
		mousePosY = (int) mousePos.y;

		int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
		int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

		int x = Global.CurrentDialogue.entity.tile[ 0 ][ 0 ].x;
		int y = Global.CurrentDialogue.entity.tile[ 0 ][ 0 ].y;

		int cx = x * Global.TileSize + offsetx + Global.TileSize / 2;
		int cy = y * Global.TileSize + offsety;

		float layoutwidth = 0;
		float layoutheight = 0;
		for ( int i = 0; i < Global.CurrentDialogue.currentInput.choices.size; i++ )
		{
			String message = ( i + 1 ) + ": " + Global.expandNames( Global.CurrentDialogue.currentInput.choices.get( i ) );

			layout.setText( font, message, tempColour, ( stage.getWidth() / 3 ) * 2, Align.left, true );
			if ( layout.width > layoutwidth )
			{
				layoutwidth = layout.width;
			}
			layoutheight += layout.height + padding;
		}

		cy -= layoutheight + 20;

		float left = cx - ( layoutwidth / 2 ) - 10;

		if ( left < 0 )
		{
			left = 0;
		}

		float right = left + layoutwidth + 20;

		if ( right >= stage.getWidth() )
		{
			left -= right - stage.getWidth();
		}

		Global.CurrentDialogue.mouseOverInput = -1;

		float voffset = padding / 2;
		for ( int i = Global.CurrentDialogue.currentInput.choices.size - 1; i >= 0; i-- )
		{
			String message = ( i + 1 ) + ": " + Global.expandNames( Global.CurrentDialogue.currentInput.choices.get( i ) );
			layout.setText( font, message, tempColour, ( stage.getWidth() / 3 ) * 2, Align.left, true );

			if ( mousePosX >= left && mousePosX <= right && mousePosY <= cy + layout.height + 10 + voffset && mousePosY >= cy + 10 + voffset )
			{
				return i;
			}

			voffset += layout.height + padding;
		}

		return -1;
	}

	// ----------------------------------------------------------------------
	public void rightClick( float screenX, float screenY )
	{
		if ( Global.CurrentDialogue != null )
		{
			// Global.CurrentDialogue.advance();
		}
		else
		{
			Vector3 mousePos = camera.unproject( new Vector3( screenX, screenY, 0 ) );

			int mousePosX = (int) mousePos.x;
			int mousePosY = (int) mousePos.y;

			int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
			int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

			int x = ( mousePosX - offsetx ) / Global.TileSize;
			int y = ( mousePosY - offsety ) / Global.TileSize;

			GameTile tile = null;
			if ( x >= 0 && x < Global.CurrentLevel.width && y >= 0 && y < Global.CurrentLevel.height )
			{
				tile = Global.CurrentLevel.getGameTile( x, y );
			}

			createContextMenu( mousePosX, mousePosY, tile );
		}
	}

	// ----------------------------------------------------------------------
	private void createContextMenu( int screenX, int screenY, GameTile tile )
	{
		Array<ActiveAbility> available = new Array<ActiveAbility>();

		for ( int i = 0; i < Global.CurrentLevel.player.slottedAbilities.size; i++ )
		{
			AbilityTree a = Global.CurrentLevel.player.slottedAbilities.get( i );
			if ( a != null && a.current.current instanceof ActiveAbility && ( (ActiveAbility) a.current.current ).isAvailable() )
			{
				available.add( (ActiveAbility) a.current.current );
			}
		}

		boolean entityWithinRange = false;
		if ( tile != null && tile.environmentEntity != null )
		{
			entityWithinRange = Math.abs( Global.CurrentLevel.player.tile[ 0 ][ 0 ].x - tile.x ) <= 1
								&& Math.abs( Global.CurrentLevel.player.tile[ 0 ][ 0 ].y - tile.y ) <= 1;
		}

		Table table = new Table();

		if ( available.size > 0 || entityWithinRange )
		{
			if ( tile != null && tile.environmentEntity != null )
			{
				final EnvironmentEntity entity = tile.environmentEntity;

				boolean hadAction = false;
				for ( final ActivationAction aa : entity.actions )
				{
					if ( !aa.visible )
					{
						continue;
					}

					Table row = new Table();

					TextButton button = new TextButton( aa.name, skin );
					row.add( button ).expand().fill();

					row.addListener( new InputListener()
					{

						@Override
						public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
						{
							return true;
						}

						@Override
						public void touchUp( InputEvent event, float x, float y, int pointer, int button )
						{
							clearContextMenu();
							aa.activate( entity );
							Global.CurrentLevel.player.tasks.add( new TaskWait() );
						}
					} );

					table.add( row ).width( Value.percentWidth( 1, table ) );
					table.row();

					hadAction = true;
				}

				if ( hadAction )
				{
					table.add( new Seperator( skin, false ) ).expandX().fillX().pad( 10 );
					table.row();
				}
			}

			boolean hadAbility = false;
			for ( final ActiveAbility aa : available )
			{
				Table row = new Table();

				row.add( new SpriteWidget( aa.Icon, 32, 32 ) );

				TextButton button = new TextButton( aa.getName(), skin );
				row.add( button ).expand().fill();

				row.addListener( new InputListener()
				{
					@Override
					public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
					{
						return true;
					}

					@Override
					public void touchUp( InputEvent event, float x, float y, int pointer, int button )
					{
						clearContextMenu();
						prepareAbility( aa );
					}
				} );

				table.add( row ).width( Value.percentWidth( 1, table ) );
				table.row();

				hadAbility = true;
			}

			if ( hadAbility )
			{
				table.add( new Seperator( skin, false ) ).expandX().fillX().pad( 10 );
				table.row();
			}
		}

		{
			Table row = new Table();

			TextButton button = new TextButton( "Rest a while", skin );
			row.add( button ).expand().fill();

			row.addListener( new InputListener()
			{
				@Override
				public boolean touchDown( InputEvent event, float x, float y, int pointer, int button )
				{
					return true;
				}

				@Override
				public void touchUp( InputEvent event, float x, float y, int pointer, int button )
				{
					clearContextMenu();
					Global.CurrentLevel.player.AI.setData( "Rest", true );
				}
			} );

			table.add( row ).width( Value.percentWidth( 1, table ) );
			table.row();
		}

		table.pack();

		displayContextMenu( table, false );
	}

	// ----------------------------------------------------------------------
	public void clearContextMenu()
	{
		if ( lockContextMenu ) { return; }

		if ( contextMenu != null )
		{
			contextMenu.remove();
			contextMenu = null;
		}
	}

	// ----------------------------------------------------------------------
	public void prepareAbility( ActiveAbility aa )
	{
		preparedAbility = aa;

		if (preparedAbility == null)
		{
			if ( abilityTiles != null )
			{
				Global.PointPool.freeAll( abilityTiles );
				abilityTiles = null;
			}

			return;
		}

		preparedAbility.setCaster( Global.CurrentLevel.player );
		preparedAbility.source = Global.CurrentLevel.player.tile[ 0 ][ 0 ];

		if ( abilityTiles != null )
		{
			Global.PointPool.freeAll( abilityTiles );
		}
		abilityTiles = preparedAbility.getValidTargets();
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean touchDown( float x, float y, int pointer, int button )
	{
		longPressed = false;
		dragged = false;
		lastZoom = 0;

		startX = x;
		startY = y;

		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean tap( float x, float y, int count, int button )
	{
		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean longPress( float x, float y )
	{
		rightClick( x, y );

		longPressed = true;
		return true;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean fling( float velocityX, float velocityY, int button )
	{
		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean pan( float x, float y, float deltaX, float deltaY )
	{
		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean panStop( float x, float y, int pointer, int button )
	{
		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean zoom( float initialDistance, float distance )
	{
		distance = initialDistance - distance;

		float amount = distance - lastZoom;
		lastZoom = distance;

		Global.TileSize -= amount / 10.0f;
		if ( Global.TileSize < 2 )
		{
			Global.TileSize = 2;
		}

		return false;
	}

	// ----------------------------------------------------------------------
	@Override
	public boolean pinch( Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2 )
	{
		return false;
	}

	// endregion GestureListener
	// ####################################################################//
	// region Private Methods

	// ----------------------------------------------------------------------
	private float roundTo( float val, float multiple )
	{
		return (float) ( multiple * Math.floor( val / multiple ) );
	}

	// ----------------------------------------------------------------------
	public void sleep( int fps )
	{
		if ( fps > 0 )
		{
			diff = System.currentTimeMillis() - start;
			long targetDelay = 1000 / fps;
			if ( diff < targetDelay )
			{
				try
				{
					Thread.sleep( targetDelay - diff );
				}
				catch ( InterruptedException e )
				{
				}
			}
			start = System.currentTimeMillis();
		}
	}

	// endregion Private Methods
	// ####################################################################//
	// region Public Methods

	// ----------------------------------------------------------------------
	public void displayContextMenu(Table content, boolean lock)
	{
		if (lockContextMenu)
		{
			return;
		}

		Table table = new Table(  );
		table.add( content ).expand().fill();

		contextMenu = new Tooltip( table, skin, stage );

		contextMenu.show( 50, 64 + 50, lock );
		lockContextMenu = lock;

		contextMenu.setWidth( Global.Resolution[ 0 ] - 120 );
		contextMenu.setHeight( Global.Resolution[ 1 ] - 64 - 100 );
	}

	// ----------------------------------------------------------------------
	public void addAbilityAvailabilityAction( Sprite sprite )
	{
		Table table = new Table();
		table.add( new SpriteWidget( sprite, 32, 32 ) ).size( Global.TileSize / 2 );
		table.addAction( new SequenceAction( Actions.moveTo( Global.Resolution[ 0 ] / 2 + Global.TileSize / 2, Global.Resolution[ 1 ]
																											   / 2
																											   + Global.TileSize
																											   + Global.TileSize
																												 / 2, 1 ), Actions.removeActor() ) );
		table.setPosition( Global.Resolution[ 0 ] / 2 + Global.TileSize / 2, Global.Resolution[ 1 ] / 2 + Global.TileSize );
		stage.addActor( table );
		table.setVisible( true );
	}

	// ----------------------------------------------------------------------
	public void addTouchAction( float x, float y )
	{
		Widget widget = new Label("O", skin);//new SpriteWidget( AssetManager.loadSprite( "Oryx/uf_split/uf_interface/uf_interface_460" ), 32, 32 );
		//widget.setScale( 0.1f );
		widget.addAction( new SequenceAction( Actions.delay( 2 ), Actions.removeActor() ) );

		widget.setPosition( x - widget.getWidth()/2, y - widget.getHeight()/2 );
		stage.addActor( widget );
		widget.setVisible( true );
	}

	// ----------------------------------------------------------------------
	public void addActorDamageAction( Entity entity )
	{
		int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
		int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

		int x = entity.tile[ 0 ][ 0 ].x;
		int y = entity.tile[ 0 ][ 0 ].y;

		int cx = x * Global.TileSize + offsetx;
		int cy = y * Global.TileSize + offsety;

		Label label = new Label( "-" + entity.damageAccumulator, skin );
		label.setColor( Color.RED );

		label.addAction( new SequenceAction( Actions.moveTo( cx, cy + Global.TileSize / 2 + Global.TileSize / 2, 0.5f ), Actions.removeActor() ) );
		label.setPosition( cx, cy + Global.TileSize / 2 );
		stage.addActor( label );
		label.setVisible( true );

		entity.damageAccumulator = 0;
	}

	// ----------------------------------------------------------------------
	public void addSpriteAction(Sprite sprite, int x, int y, int width, int height)
	{
		float duration = sprite.getLifetime();

		Table table = new Table();
		table.add( new SpriteWidget( sprite, width, height ) );
		table.addAction( Actions.delay( duration, Actions.removeActor() ) );
		table.setPosition( x, y );
		stage.addActor( table );
		table.setVisible( true );
	}

	// ----------------------------------------------------------------------
	public void addActorExperienceAction( Entity entity, int essence )
	{
		int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
		int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

		int x = entity.tile[ 0 ][ 0 ].x;
		int y = entity.tile[ 0 ][ 0 ].y;

		int cx = x * Global.TileSize + offsetx;
		int cy = y * Global.TileSize + offsety;

		Label label = new Label( "+" + essence + " exp", skin );
		label.setColor( Color.YELLOW );

		label.addAction( new SequenceAction( Actions.moveTo( cx, cy + Global.TileSize / 2 + Global.TileSize / 2, 0.5f ), Actions.removeActor() ) );
		label.setPosition( cx, cy + Global.TileSize / 2 );
		stage.addActor( label );
		label.setVisible( true );
	}

	// ----------------------------------------------------------------------
	public void addActorItemPickupAction( Entity entity, Item item )
	{
		int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
		int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

		int x = entity.tile[ 0 ][ 0 ].x;
		int y = entity.tile[ 0 ][ 0 ].y;

		int cx = x * Global.TileSize + offsetx;
		int cy = y * Global.TileSize + offsety;

		Label label = new Label( "Picked up " + item.name + " (x" + item.count + ")", skin );
		label.setColor( Color.ORANGE );

		label.addAction( new SequenceAction( Actions.moveTo( cx, cy + Global.TileSize / 2 + Global.TileSize / 2, 0.5f ), Actions.removeActor() ) );
		label.setPosition( cx, cy + Global.TileSize / 2 );
		stage.addActor( label );
		label.setVisible( true );
	}

	// ----------------------------------------------------------------------
	public void addActorHealingAction( Entity entity )
	{
		int offsetx = Global.Resolution[ 0 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].x * Global.TileSize;
		int offsety = Global.Resolution[ 1 ] / 2 - Global.CurrentLevel.player.tile[ 0 ][ 0 ].y * Global.TileSize;

		int x = entity.tile[ 0 ][ 0 ].x;
		int y = entity.tile[ 0 ][ 0 ].y;

		int cx = x * Global.TileSize + offsetx;
		int cy = y * Global.TileSize + offsety;

		Label label = new Label( "+" + entity.healingAccumulator, skin );
		label.setColor( Color.GREEN );

		label.addAction( new SequenceAction( Actions.moveTo( cx, cy + Global.TileSize / 2 + Global.TileSize / 2, 0.5f ), Actions.removeActor() ) );
		label.setPosition( cx, cy + Global.TileSize / 2 );
		stage.addActor( label );
		label.setVisible( true );

		entity.healingAccumulator = 0;
	}

	// ----------------------------------------------------------------------
	public void addFullScreenMessage( String message )
	{
		Label label = new Label( message, skin, "title" );
		label.setColor( Color.WHITE );

		int cx = 50;
		int cy = Global.Resolution[ 1 ] - 50;

		label.addAction( new SequenceAction( Actions.moveTo( cx + 25, cy, 2.5f ), Actions.removeActor() ) );
		label.setPosition( cx, cy );
		stage.addActor( label );
		label.setVisible( true );
	}

	// endregion Public Methods
	// ####################################################################//
	// region Data

	// ----------------------------------------------------------------------
	public enum RenderLayer
	{
		GROUNDTILE, GROUNDFIELD, GROUNDENTITY,

		ITEM, ESSENCE, CURSOR,

		RAISEDENTITY,

		EFFECT, ABILITY,

		OVERHEADENTITY, OVERHEADFIELD, OVERHANG,

		SEENFOG, UNSEENFOG
	}

	// ----------------------------------------------------------------------
	public boolean examineMode = false;

	// ----------------------------------------------------------------------
	private static final float ScreenShakeSpeed = 0.02f;

	// ----------------------------------------------------------------------
	public static GameScreen Instance;
	private final GlyphLayout layout = new GlyphLayout();
	private final Color temp = new Color();

	// ----------------------------------------------------------------------
	public GestureDetector gestureDetector;

	// ----------------------------------------------------------------------
	public OrthographicCamera camera;

	// ----------------------------------------------------------------------
	private Tooltip contextMenu;
	public boolean lockContextMenu;

	// ----------------------------------------------------------------------
	public DragDropPayload dragDropPayload;
	public float screenShakeRadius;
	public float screenShakeAngle;
	public InputMultiplexer inputMultiplexer;
	public boolean mouseOverUI;
	public Array<Item> pickupQueue = new Array<Item>( false, 16 );

	// ----------------------------------------------------------------------
	public ActiveAbility preparedAbility;
	public AbilityTree abilityToEquip;
	private Array<Point> abilityTiles;
	private Color tempColour = new Color();
	//private Tooltip tooltip;

	// ----------------------------------------------------------------------
	private PriorityQueue<RenderSprite> queuedSprites = new PriorityQueue<RenderSprite>( );
	private Array<Entity> hasStatus = new Array<Entity>();
	private Array<Entity> entitiesWithSpeech = new Array<Entity>();

	// ----------------------------------------------------------------------
	private Pool<RenderSprite> renderSpritePool = Pools.get( RenderSprite.class );

	// ----------------------------------------------------------------------
	private Sprite border;
	private int mousePosX;
	private int mousePosY;
	private Stage stage;
	private SpriteBatch batch;
	private TextureRegion blank;
	private TextureRegion white;
	private Sprite bag;
	private EnumMap<GameTile.OrbType, Sprite> orbs = new EnumMap<GameTile.OrbType, Sprite>( GameTile.OrbType.class );
	private TextureRegion speechBubbleArrow;
	private NinePatch speechBubbleBackground;
	private float frametime;
	private BitmapFont font;
	private BitmapFont hightlightfont;
	private float screenShakeAccumulator;
	private TilingSprite fogSprite;
	private static final Color seenFogCol = new Color( 0, 0, 0, 0.5f );
	private static final Color unseenFogCol = new Color( 0, 0, 0, 1 );

	// ----------------------------------------------------------------------
	private AbilityPanel abilityPanel;
	private EquipmentPanel equipmentPanel;
	private Skin skin;
	private Button sheathButton;

	// ----------------------------------------------------------------------
	private long diff, start = System.currentTimeMillis();

	// ----------------------------------------------------------------------
	private float lastZoom;

	// ----------------------------------------------------------------------
	private boolean created;

	// ----------------------------------------------------------------------
	private boolean longPressed;
	private boolean dragged;
	private float startX;
	private float startY;

	// ----------------------------------------------------------------------
	private int fps;
	private float storedFrametime;
	private float fpsAccumulator;

	private final EnumBitflag<Direction> directionBitflag = new EnumBitflag<Direction>( );

	// endregion Data
	// ####################################################################//
	// region Classes

	public static class RenderSprite implements Comparable<RenderSprite>
	{
		public final Color colour = new Color();
		public Sprite sprite;
		public RenderLayer layer;
		public int x;
		public int y;
		public int width;
		public int height;
		public int index;

		public RenderSprite set( Sprite sprite, Color colour, int x, int y, int width, int height, RenderLayer layer, int index )
		{
			this.sprite = sprite;
			this.colour.set( colour );
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.layer = layer;
			this.index = index;

			return this;
		}

		@Override
		public int compareTo( RenderSprite o )
		{
			int comp = layer.ordinal() - o.layer.ordinal();

			if ( comp == 0 )
			{
				comp = o.y - y;
			}

			if ( comp == 0 )
			{
				comp = o.x - x;
			}

			if ( comp == 0 )
			{
				comp = index - o.index;
			}

			return comp;
		}
	}

	public static class GrayscaleShader
	{
		static String vertexShader = "attribute vec4 a_position;\n"
									 + "attribute vec4 a_color;\n"
									 + "attribute vec2 a_texCoord0;\n"
									 + "\n"
									 + "uniform mat4 u_projTrans;\n"
									 + "\n"
									 + "varying vec4 v_color;\n"
									 + "varying vec2 v_texCoords;\n"
									 + "\n"
									 + "void main() {\n"
									 + "    v_color = a_color;\n"
									 + "    v_texCoords = a_texCoord0;\n"
									 + "    gl_Position = u_projTrans * a_position;\n"
									 + "}";

		static String fragmentShader = "#ifdef GL_ES\n"
									   + "    precision mediump float;\n"
									   + "#endif\n"
									   + "\n"
									   + "varying vec4 v_color;\n"
									   + "varying vec2 v_texCoords;\n"
									   + "uniform sampler2D u_texture;\n"
									   + "\n"
									   + "void main() {\n"
									   + "  vec4 c = v_color * texture2D(u_texture, v_texCoords);\n"
									   + "  float grey = (c.r + c.g + c.b) / 3.0;\n"
									   + "  gl_FragColor = vec4(grey, grey, grey, c.a);\n"
									   + "}";

		public static ShaderProgram Instance = new ShaderProgram( vertexShader, fragmentShader );
	}

	// endregion Classes
	// ####################################################################//
}
