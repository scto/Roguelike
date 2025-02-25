package Roguelike.Levels;

import Roguelike.Ability.AbilityTree;
import Roguelike.Ability.ActiveAbility.ActiveAbility;
import Roguelike.AssetManager;
import Roguelike.DungeonGeneration.DungeonFileParser.DFPRoom;
import Roguelike.Entity.ActivationAction.ActivationActionGroup;
import Roguelike.Entity.Entity;
import Roguelike.Entity.EnvironmentEntity;
import Roguelike.Entity.GameEntity;
import Roguelike.Entity.Tasks.AbstractTask;
import Roguelike.Entity.Tasks.TaskAttack;
import Roguelike.Entity.Tasks.TaskMove;
import Roguelike.Entity.Tasks.TaskWait;
import Roguelike.Fields.Field;
import Roguelike.Fields.Field.FieldLayer;
import Roguelike.GameEvent.AdditionalSprite;
import Roguelike.GameEvent.GameEventHandler;
import Roguelike.Global;
import Roguelike.Global.Direction;
import Roguelike.Global.Passability;
import Roguelike.Global.Statistic;
import Roguelike.Items.Inventory;
import Roguelike.Items.Item;
import Roguelike.Items.TreasureGenerator;
import Roguelike.Lights.Light;
import Roguelike.Pathfinding.Pathfinder;
import Roguelike.Pathfinding.ShadowCastCache;
import Roguelike.Pathfinding.ShadowCaster;
import Roguelike.RoguelikeGame;
import Roguelike.RoguelikeGame.ScreenEnum;
import Roguelike.Screens.GameScreen;
import Roguelike.Sound.RepeatingSoundEffect;
import Roguelike.Sound.SoundInstance;
import Roguelike.Sprite.Sprite;
import Roguelike.Sprite.SpriteAnimation.BumpAnimation;
import Roguelike.Sprite.SpriteAnimation.MoveAnimation;
import Roguelike.Sprite.SpriteAnimation.MoveAnimation.MoveEquation;
import Roguelike.Sprite.SpriteEffect;
import Roguelike.Tiles.GameTile;
import Roguelike.Tiles.Point;
import Roguelike.UI.Message;
import Roguelike.Util.EnumBitflag;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;

public class Level
{
	// ####################################################################//
	// region Constructor

	public Level( GameTile[][] grid )
	{
		this.Grid = grid;
		this.width = grid.length;
		this.height = grid[0].length;
	}

	// endregion Constructor
	// ####################################################################//
	// region Lights

	public void calculateAmbient()
	{
		Color acol = new Color( Ambient );
		acol.mul( acol.a );
		acol.a = 1;

		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				GameTile tile = Grid[x][y];
				tile.ambientColour.set( acol );
			}
		}

		// do shadows
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				GameTile tile = Grid[x][y];
				if ( tile.tileData.shadow != null )
				{
					//tile.tileData.shadow.apply( Grid, x, y );
				}
			}
		}
	}

	private void calculateLight( float delta, Array<Light> lights )
	{
		for ( Light l : lights )
		{
			l.update( delta );
			calculateSingleLight( l );
			if ( l.copied )
			{
				Global.LightPool.free( l );
			}
		}
	}

	private void calculateSingleLight( Light l )
	{
		Array<Point> output = l.shadowCastCache.getShadowCast( Grid, (int) l.lx, (int) l.ly, (int) l.baseIntensity + 1, null );

		for ( Point tilePos : output )
		{
			GameTile tile = getGameTile( tilePos );

			if (!tile.visible)
			{
				continue;
			}

			float dst = 1 - Vector2.dst2( l.lx, l.ly, tile.x, tile.y ) / ( l.actualIntensity * l.actualIntensity );
			if ( dst < 0 )
			{
				dst = 0;
			}

			tempColour.set( l.colour );
			tempColour.mul( tempColour.a );
			tempColour.a = 1;

			tempColour.mul( dst );

			tile.light.add( tempColour );
		}
	}

	private void getLightsForTile( GameTile tile, Array<Light> output, int viewRange )
	{
		int lx = tile.x;
		int ly = tile.y;

		int px = player.tile[0][0].x;
		int py = player.tile[0][0].y;

		if ( tile.lightObj != null )
		{
			if ( checkLightCloseEnough( lx, ly, (int) tile.lightObj.baseIntensity, px, py, viewRange ) )
			{
				tile.lightObj.lx = lx;
				tile.lightObj.ly = ly;
				output.add( tile.lightObj );
			}
		}

		if ( tile.hasFieldLight )
		{
			for ( FieldLayer layer : FieldLayer.values() )
			{
				Field field = tile.fields.get( layer );
				if ( field != null )
				{
					Field.SpriteGroup group = field.getSpriteGroup();
					if ( group.light != null && checkLightCloseEnough( lx, ly, (int) group.light.baseIntensity, px, py, viewRange ) )
					{
						group.light.lx = lx;
						group.light.ly = ly;
						output.add( group.light );
					}
				}
			}
		}

		if ( tile.environmentEntity != null && tile.environmentEntity.light != null && tile.environmentEntity.tile[0][0] == tile )
		{
			if ( checkLightCloseEnough( lx, ly, (int) tile.environmentEntity.light.baseIntensity, px, py, viewRange ) )
			{
				tile.environmentEntity.light.lx = lx;
				tile.environmentEntity.light.ly = ly;
				output.add( tile.environmentEntity.light );
			}
		}

		if ( tile.spriteEffects.size > 0 )
		{
			for ( SpriteEffect se : tile.spriteEffects )
			{
				if ( se.light != null )
				{
					if ( checkLightCloseEnough( lx, ly, (int) se.light.baseIntensity, px, py, viewRange ) )
					{
						se.light.lx = lx;
						se.light.ly = ly;
						output.add( se.light );
					}
				}
			}
		}

		if ( tile.entity != null && tile.entity.tile[0][0] == tile )
		{
			tempLightList.clear();
			tile.entity.getLight( tempLightList );
			for ( Light l : tempLightList )
			{
				if ( checkLightCloseEnough( lx, ly, (int) l.baseIntensity + 1, px, py, viewRange ) )
				{
					l.lx = lx;
					l.ly = ly;

					if ( tile.entity.sprite.spriteAnimation != null )
					{
						int[] offset = tile.entity.sprite.spriteAnimation.getRenderOffset();
						l.lx += (float) offset[0] / (float) Global.TileSize;
						l.ly += (float) offset[1] / (float) Global.TileSize;
					}

					output.add( l );
				}
			}
		}
	}

	private boolean checkLightCloseEnough( int lx, int ly, int intensity, int px, int py, int viewRange )
	{
		return Math.max( Math.abs( px - lx ), Math.abs( py - ly ) ) <= viewRange + intensity;

	}

	// endregion Lights
	// ####################################################################//
	// region Cleanup

	private void cleanUpDeadForTile( GameTile tile )
	{
		{
			GameEntity e = tile.entity;
			if ( e != null )
			{
				if ( tile.visible )
				{
					if ( e.canTakeDamage && e.hasDamage )
					{
						e.pendingMessages.add( new Message( "-"+e.damageAccumulator, Color.RED ) );
						e.damageAccumulator = 0;
						e.hasDamage = false;
					}

					if ( e.canTakeDamage && e.healingAccumulator > 0 )
					{
						e.pendingMessages.add( new Message( "+"+e.healingAccumulator, Color.GREEN ) );
						e.healingAccumulator = 0;
					}
				}
				else
				{
					e.damageAccumulator = 0;
					e.healingAccumulator = 0;
				}

				if ( e.canTakeDamage && e != player && e.HP <= 0 && !hasActiveEffects( e ) )
				{
					int quality = Global.getQuality();

					if ( e.isBoss )
					{
						e.inventory.m_items.addAll( TreasureGenerator.generateLoot( quality + 1, "random", MathUtils.random ) );
					}
					else if ( e.essence > 0 && MathUtils.random( 4 ) == 0 )
					{
						e.inventory.m_items.addAll( TreasureGenerator.generateLoot( quality, "random", MathUtils.random ) );
					}

					entityDeathSound.play( e.tile[0][0] );

					dropItems( e.getInventory(), e.tile[0][0], e.essence, e );
					e.removeFromTile();
				}
				else if ( e == player && e.HP <= 0 && !hasActiveEffects( e ) )
				{
					GameScreen.Instance.displayGameOverMessage();
				}

				if ( e.popupDuration <= 0 && e.popup != null && e.popup.length() == e.displayedPopup.length() && e.popupFade <= 0 )
				{
					e.popupFade = 1;
				}
			}
		}

		{
			EnvironmentEntity e = tile.environmentEntity;
			if ( e != null )
			{
				if ( e.canTakeDamage && e.damageAccumulator > 0 && tile.visible )
				{
					e.pendingMessages.add( new Message( "-"+e.damageAccumulator, Color.RED ) );
					e.damageAccumulator = 0;
					e.hasDamage = false;
				}

				if ( e.canTakeDamage && e.HP <= 0 && !hasActiveEffects( e ) )
				{
					dropItems( e.getInventory(), e.tile[0][0], e.essence, e );

					for ( ActivationActionGroup group : e.onDeathActions)
					{
						if (group.enabled)
						{
							group.activate( e, 1 );
						}
					}

					e.removeFromTile();
				}

				if ( e.popupDuration <= 0 && e.popupFade <= 0 )
				{
					e.popupFade = 1;
				}
			}
		}
	}

	private void dropItems( Inventory inventory, GameTile source, int essence, Object obj )
	{
		Array<Point> possibleTiles = new Array<Point>();
		for (Direction dir : Direction.values())
		{
			if ( Global.CanMoveDiagonal || dir.isCardinal() )
			{
				int nx = source.x + dir.getX();
				int ny = source.y + dir.getY();
				GameTile tile = getGameTile( nx, ny );
				if ( tile != null && tile.getPassable( ItemDropPassability, obj ) )
				{
					possibleTiles.add( Global.PointPool.obtain().set( nx, ny ) );
				}
			}
		}

		float delay = 0;

		if (essence > 0)
		{
			delay = dropOrbs( essence, delay, GameTile.OrbType.EXPERIENCE, source, possibleTiles );

			if ( MathUtils.random( 5 ) <= Global.LevelManager.hpDropCounter )
			{
				Global.LevelManager.hpDropCounter -= 3;
				int amount = Math.max( 10, player.getMaxHP() / 5 );
				delay += dropOrbs( amount, delay, GameTile.OrbType.HEALTH, source, possibleTiles );
			}
			else
			{
				Global.LevelManager.hpDropCounter++;
			}

			if ( obj instanceof GameEntity && ((GameEntity)obj).isBoss )
			{
				int amount = Math.max( 10, player.getMaxHP() );
				delay += dropOrbs( amount, delay, GameTile.OrbType.HEALTH, source, possibleTiles );
			}
		}

		for ( Item i : inventory.m_items )
		{
			if ( i.canDrop && i.shouldDrop() )
			{
				Point target = possibleTiles.size > 0 ? possibleTiles.random() : Global.PointPool.obtain().set( source );
				GameTile tile = getGameTile( target );

				if (tile.entity == player)
				{
					GameScreen.Instance.pickupQueue.add( i );
				}
				else
				{
					tile.items.add( i );
				}

				int[] diff = tile.getPosDiff( source );

				MoveAnimation anim = new MoveAnimation( 0.2f, diff, MoveEquation.LEAP );
				anim.leapHeight = 3;
				i.getIcon().spriteAnimation = anim;
				i.getIcon().renderDelay = delay;
				delay += 0.015f;
			}
		}

		Global.PointPool.freeAll( possibleTiles );

		pickupOrbs();
	}

	private float dropOrbs( int val, float delay, GameTile.OrbType type, GameTile source, Array<Point> possibleTiles)
	{
		if ( val > 0 )
		{
			if ( possibleTiles.size > 0 )
			{
				int blockSize = MathUtils.clamp( val / 10, 10, 100 );

				while ( val > 0 )
				{
					int block = Math.min( blockSize, val );
					val -= block;

					Point target = possibleTiles.random();
					GameTile tile = getGameTile( target );

					int existingVal = tile.orbs.containsKey( type ) ? tile.orbs.get( type ) : 0;
					tile.orbs.put( type, existingVal + block );

					int[] diff = tile.getPosDiff( source );

					Sprite sprite = AssetManager.loadSprite( type.spriteName );
					MoveAnimation anim = new MoveAnimation( 0.2f, diff, MoveEquation.LEAP );
					anim.leapHeight = 3;
					sprite.spriteAnimation = anim;
					sprite.renderDelay = delay;
					delay += 0.02f;

					float scale = 0.5f + 0.5f * ( MathUtils.clamp( block, 10.0f, 1000.0f ) / 1000.0f );
					sprite.baseScale[0] = scale;
					sprite.baseScale[1] = scale;

					tile.spriteEffects.add( new SpriteEffect( sprite, Direction.CENTER, null ) );
				}
			}
			else
			{
				int existingVal = source.orbs.containsKey( type ) ? source.orbs.get( type ) : 0;
				source.orbs.put( type, existingVal + val );

				int[] diff = new int[] { 0, 0 };

				Sprite sprite = AssetManager.loadSprite( type.spriteName );
				MoveAnimation anim = new MoveAnimation( 0.2f, diff, MoveEquation.LEAP );
				anim.leapHeight = 3;
				sprite.spriteAnimation = anim;
				sprite.renderDelay = delay;
				delay += 0.02f;

				float scale = 0.5f + 0.5f * ( MathUtils.clamp( val, 10.0f, 1000.0f ) / 1000.0f );
				sprite.baseScale[0] = scale;
				sprite.baseScale[1] = scale;

				source.spriteEffects.add( new SpriteEffect( sprite, Direction.CENTER, null ) );
			}
		}

		return delay;
	}

	private void clearEffectsForTile( GameTile tile )
	{
		if ( tile.spriteEffects.size > 0 )
		{
			tile.spriteEffects.clear();
		}

		if ( tile.environmentEntity != null)
		{
			tile.environmentEntity.pendingMessages.clear();
			tile.environmentEntity.extraUIHP = 0;
			if (tile.environmentEntity.sprite != null)
			{
				tile.environmentEntity.sprite.spriteAnimation = null;
			}
		}

		if ( tile.entity != null )
		{
			tile.entity.extraUIHP = 0;
			tile.entity.pendingMessages.clear();
			if ( tile.entity.sprite != null )
			{
				tile.entity.sprite.spriteAnimation = null;
			}
		}

		if ( tile.items.size > 0 )
		{
			for ( Item i : tile.items )
			{
				i.getIcon().spriteAnimation = null;
			}
		}
	}

	// endregion Cleanup
	// ####################################################################//
	// region Update

	// ----------------------------------------------------------------------
	public void updateVisibleTiles()
	{
		if ( !isVisionRestricted )
		{
			for ( int x = 0; x < width; x++ )
			{
				for ( int y = 0; y < height; y++ )
				{
					Grid[x][y].visible = true;
					Grid[x][y].seen = true;

					Grid[x][y].seenBitflag.setAll( Direction.class );
					Grid[x][y].unseenBitflag.setAll( Direction.class );
				}
			}
		}
		else
		{
			for ( int x = 0; x < width; x++ )
			{
				for ( int y = 0; y < height; y++ )
				{
					Grid[x][y].tempVisible = Grid[x][y].visible;
					Grid[x][y].visible = false;
				}
			}

			shadowCastStore.clear();
			shadowCastStore.addAll( visibilityData.getCurrentShadowCast() );
			Array<Point> output = visibilityData.getShadowCast( Grid, player.tile[0][0].x, player.tile[0][0].y, player.getVariable( Statistic.PERCEPTION ), player, true );

			for ( Point tilePos : output )
			{
				GameTile tile = getGameTile( tilePos );
				if ( tile != null )
				{
					tile.visible = true;

					if (!tile.seen)
					{
						tile.seen = true;
						updateUnseenBitflag( tilePos.x, tilePos.y );
					}
				}
			}

			for ( int x = 0; x < width; x++ )
			{
				for ( int y = 0; y < height; y++ )
				{
					GameTile tile = Grid[x][y];

					if (tile.tempVisible != tile.visible)
					{
						updateSeenBitflag( x, y );
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	public void updateSeenBitflag(int x, int y)
	{
		for (Direction dir : Direction.values())
		{
			GameTile tile = getGameTile( x + dir.getX(), y + dir.getY() );

			if (tile != null)
			{
				buildTilingBitflag( tile.seenBitflag, tile.x, tile.y, SEENID );
			}
		}
	}

	// ----------------------------------------------------------------------
	public void updateUnseenBitflag(int x, int y)
	{
		for (Direction dir : Direction.values())
		{
			GameTile tile = getGameTile( x + dir.getX(), y + dir.getY() );

			if (tile != null)
			{
				buildTilingBitflag( tile.unseenBitflag, tile.x, tile.y, UNSEENID );
			}
		}
	}

	// ----------------------------------------------------------------------
	public void buildTilingBitflag(EnumBitflag<Direction> bitflag, int x, int y, long id)
	{
		// Build bitflag of surrounding tiles
		bitflag.clear();
		for (Direction dir : Direction.values())
		{
			GameTile otile = getGameTile( x + dir.getX(), y + dir.getY() );

			if (otile != null)
			{
				// Attempt to find match
				boolean matchFound = false;

				if (otile.spriteGroup.tilingSprite != null && otile.spriteGroup.tilingSprite.thisID == id)
				{
					matchFound = true;
				}
				else if (otile.environmentEntity != null && otile.environmentEntity.tilingSprite != null && otile.environmentEntity.tilingSprite.thisID == id)
				{
					matchFound = true;
				}
				else if (!otile.seen && id == UNSEENID)
				{
					matchFound = true;
				}
				else if (!otile.visible && id == SEENID)
				{
					matchFound = true;
				}

				if (!matchFound)
				{
					if (otile.hasFields)
					{
						for (FieldLayer layer : FieldLayer.values())
						{
							Field field = otile.fields.get( layer );
							if (field != null)
							{
								Field.SpriteGroup group = field.getSpriteGroup();
								if (group.tilingSprite != null && group.tilingSprite.thisID == id)
								{
									matchFound = true;
									break;
								}
							}
						}
					}
				}

				if (!matchFound)
				{
					bitflag.setBit( dir );
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private void updatePopupsForTile( GameTile tile, float delta )
	{
		if ( tile.entity != null && tile.entity.tile[0][0] == tile )
		{
			if (tile.entity.dialogue != null)
			{
				if (tile.entity.dialogue.popupText != null)
				{
					tile.entity.setPopupText( tile.entity.dialogue.popupText, 2 );
					tile.entity.dialogue.popupText = null;
				}

				if (tile.entity.dialogue.soundToBePlayed != null)
				{
					tile.entity.dialogue.soundToBePlayed.play( tile.entity.tile[0][0] );
					tile.entity.dialogue.soundToBePlayed = null;
				}
			}

			if ( tile.entity.popup != null )
			{
				if ( tile.entity.displayedPopup.length() < tile.entity.popup.length() )
				{
					tile.entity.popupAccumulator += delta;

					while ( tile.entity.popupAccumulator >= 0 && tile.entity.displayedPopup.length() < tile.entity.popup.length() )
					{
						tile.entity.popupAccumulator -= 0.02f;

						tile.entity.displayedPopup = tile.entity.popup.substring( 0, tile.entity.displayedPopup.length() + 1 );
					}
				}
			}

			GameScreen.Instance.processMessageQueue( tile.entity, delta );
		}

		if ( tile.environmentEntity != null && tile.environmentEntity.tile[0][0] == tile )
		{
			if ( tile.environmentEntity.popup != null )
			{
				if ( tile.environmentEntity.displayedPopup.length() < tile.environmentEntity.popup.length() )
				{
					tile.environmentEntity.popupAccumulator += delta;

					while ( tile.environmentEntity.popupAccumulator >= 0 && tile.environmentEntity.displayedPopup.length() < tile.environmentEntity.popup.length() )
					{
						tile.environmentEntity.popupAccumulator -= 0.02f;

						tile.environmentEntity.displayedPopup = tile.environmentEntity.popup.substring( 0, tile.environmentEntity.displayedPopup.length() + 1 );
					}
				}
			}

			GameScreen.Instance.processMessageQueue( tile.environmentEntity, delta );
		}
	}

	// ----------------------------------------------------------------------
	private void updateExtraUIHPForTile( GameTile tile, float delta )
	{
		if (tile.entity != null && tile.entity.extraUIHP > 0 && tile.entity.tile[0][0] == tile)
		{
			tile.entity.extraUIHPAccumulator += delta;

			while (tile.entity.extraUIHP > 0 && tile.entity.extraUIHPAccumulator > 0)
			{
				tile.entity.extraUIHP--;

				float ratio = (float)tile.entity.extraUIHP / (float)(tile.entity.getMaxHP());
				ratio = 0.02f - 0.02f * ratio;

				tile.entity.extraUIHPAccumulator -= ratio;
			}
		}

		if (tile.environmentEntity != null && tile.environmentEntity.extraUIHP > 0 && tile.environmentEntity.tile[0][0] == tile)
		{
			tile.environmentEntity.extraUIHPAccumulator += delta;

			while (tile.environmentEntity.extraUIHP > 0 && tile.environmentEntity.extraUIHPAccumulator > 0)
			{
				tile.environmentEntity.extraUIHP--;

				float ratio = (float)tile.environmentEntity.extraUIHP / (float)(tile.environmentEntity.getMaxHP());
				ratio = 0.02f - 0.02f * ratio;

				tile.environmentEntity.extraUIHPAccumulator -= ratio;
			}
		}
	}

	// ----------------------------------------------------------------------
	private void updateSpriteEffectsForTile( GameTile tile, float delta )
	{
		if ( tile.spriteEffects.size > 0 )
		{
			Iterator<SpriteEffect> itr = tile.spriteEffects.iterator();
			while ( itr.hasNext() )
			{
				SpriteEffect e = itr.next();
				boolean finished = e.Sprite.update( delta );

				if ( finished )
				{
					itr.remove();
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private void updateSpritesForTile( GameTile tile, float delta )
	{
		for ( Sprite sprite : tile.getSprites() )
		{
			sprite.update( delta );
		}

		if ( tile.getTilingSprite() != null )
		{
			//for (Sprite sprite : tile.tileData.tilingSprite.sprites)
			//{
			//	sprite.update( delta );
			//}
		}

		if ( tile.hasFields )
		{
			for ( FieldLayer layer : FieldLayer.values() )
			{
				Field field = tile.fields.get( layer );
				if ( field != null )
				{
					Field.SpriteGroup group = field.getSpriteGroup();
					if (group.sprite != null)
					{
						group.sprite.update( delta );
					}
				}
			}
		}

		if ( tile.environmentEntity != null && tile.environmentEntity.tile[0][0] == tile && tile.environmentEntity.sprite != null )
		{
			tile.environmentEntity.sprite.update( delta );

			if (tile.environmentEntity.replacementSprite != null)
			{
				tile.environmentEntity.replacementSprite.update( delta );
			}
			for (AdditionalSprite s : tile.environmentEntity.additionalSprites)
			{
				s.sprite.update( delta );
			}
		}

		if ( tile.entity != null && tile.entity.tile[0][0] == tile && tile.entity.sprite != null )
		{
			tile.entity.sprite.update( delta );

			if (tile.entity.replacementSprite != null)
			{
				tile.entity.replacementSprite.update( delta );
			}
			for (AdditionalSprite s : tile.entity.additionalSprites)
			{
				s.sprite.update( delta );
			}
		}

		if ( tile.items.size > 0 )
		{
			for ( Item i : tile.items )
			{
				i.getIcon().update( delta );
			}
		}
	}

	// endregion Update
	// ####################################################################//
	// region Process

	private void processPlayer()
	{
		player.updatedAbilityDam = false;
		player.updatedAbilityHeal = false;

		AbstractTask task = player.tasks.removeIndex( 0 );
		for ( GameEventHandler handler : player.getAllHandlers() )
		{
			handler.onTask( player, task );
		}

		if ( !task.cancel )
		{
			task.processTask( player );
		}

		if (task instanceof TaskMove)
		{
			for (AbilityTree ab : player.slottedAbilities)
			{
				if (ab != null)
				{
					ab.current.current.onMove();
				}
			}
		}
		else if (task instanceof TaskAttack)
		{
			for (AbilityTree ab : player.slottedAbilities)
			{
				if (ab != null)
				{
					ab.current.current.onAttack();
				}
			}
		}
		else if (task instanceof TaskWait)
		{
			for (AbilityTree ab : player.slottedAbilities)
			{
				if (ab != null)
				{
					ab.current.current.onWait();
				}
			}
		}

		if ( !(task instanceof TaskMove ) )
		{
			for (int x = 0; x < player.size; x++)
			{
				for (int y = 0; y < player.size; y++)
				{
					player.tile[x][y].prevEntity = player;
				}
			}
		}

		float actionCost = task.cost * player.getActionDelay();

		Global.AUT += actionCost;
		Global.DayNightFactor = (float) ( 0.1f + ( ( ( Math.sin( Global.AUT / 100.0f ) + 1.0f ) / 2.0f ) * 0.9f ) );

		player.update( actionCost );

		// Advance all entity accumulators and build list
		getAllEntitiesToBeProcessed( actionCost );

		tempEnvironmentEntityList.clear();
		getAllEnvironmentEntitiesToBeProcessed( tempEnvironmentEntityList );
		for ( EnvironmentEntity ee : tempEnvironmentEntityList )
		{
			ee.update( actionCost );
		}

		tempFieldList.clear();
		getAllFields( tempFieldList );
		for ( Field f : tempFieldList )
		{
			if ( f.tile == null )
			{
				continue;
			}

			f.update( actionCost );
			if ( f.stacks < 1 )
			{
				f.tile.fields.put( f.layer, null );
				f.tile = null;
			}
		}

		pickupOrbs();

		if ( task instanceof TaskMove && player.tile[0][0].items.size > 0 )
		{
			for ( Item item : player.tile[0][0].items )
			{
				GameScreen.Instance.pickupQueue.add( item );
			}

			pickupItemSound.play( player.tile[ 0 ][ 0 ] );

			player.tile[0][0].items.clear();
		}

		player.tile[0][0].processFieldEffectsForEntity( player, actionCost );

		// check if enemy visible
		if ( enemyVisible() )
		{
			// Clear pending moves
			player.AI.setData( "Pos", null );
			player.AI.setData( "Rest", null );
		}

		if ( player.sprite.spriteAnimation instanceof BumpAnimation )
		{
			player.AI.setData( "Pos", null );
			player.AI.setData( "Rest", null );
		}
	}

	public void pickupOrbs()
	{
		if ( player.tile[ 0 ][ 0 ].orbs.size > 0 )
		{
			for ( GameTile.OrbType type : GameTile.OrbType.values() )
			{
				if ( player.tile[ 0 ][ 0 ].orbs.containsKey( type ) )
				{
					int val = player.tile[ 0 ][ 0 ].orbs.get( type );

					if ( type == GameTile.OrbType.EXPERIENCE )
					{
						player.pendingMessages.add( new Message( "+" + val + " xp", Color.YELLOW ) );
						for (AbilityTree tree : player.slottedAbilities)
						{
							if (tree != null)
							{
								tree.current.gainExp( val );
							}
						}
						pickupXPSound.play( player.tile[ 0 ][ 0 ] );

						player.tile[ 0 ][ 0 ].orbs.remove( type );
					}
					else if ( type == GameTile.OrbType.HEALTH )
					{
						int healthMissing = player.getMaxHP() - player.HP;

						int restored = Math.min( healthMissing, val );

						if (restored > 0)
						{
							player.applyHealing( restored );
							pickupHPSound.play( player.tile[ 0 ][ 0 ] );
							player.tile[ 0 ][ 0 ].orbs.remove( type );

							if (val - restored > 0)
							{
								player.tile[ 0 ][ 0 ].orbs.put( GameTile.OrbType.HEALTH, val - restored );
							}
						}
					}
				}
			}
		}
	}

	public boolean isInTurn()
	{
		return turnCount >= 0 && turnCount < 100;
	}

	public boolean canStartTurn()
	{
		return !hasActiveEffects() && Global.CurrentDialogue == null && updateAccumulator >= updateDeltaStep && player.AI != null;
	}

	public void startTurn()
	{
		turnCount = 0;
	}

	public void doTurnWork()
	{
		if (turnCount == -1)
		{
			turnCount = 0;
		}
		else if (turnCount == 0)
		{
			player.AI.update( player );

			if ( player.tasks.size > 0 )
			{
				turnCount = 1;
			}
		}
		else if (turnCount == 1)
		{
			processPlayer();
			player.updateShadowCast();
			turnCount = 2;
		}
		else if (turnCount == 2)
		{
			if ( toBeProcessedList.size > 0 )
			{
				GameEntity e = toBeProcessedList.get( 0 );
				boolean complete = processEntityTurn( e );

				if (complete)
				{
					toBeProcessedList.removeIndex( 0 );
				}
			}

			if ( ActiveAbilities.size > 0 )
			{
				Iterator<ActiveAbility> itr = ActiveAbilities.iterator();
				while ( itr.hasNext() )
				{
					ActiveAbility aa = itr.next();
					boolean finished = aa.update();

					if ( finished )
					{
						itr.remove();
					}
				}
			}

			if ( NewActiveAbilities.size > 0 )
			{
				ActiveAbilities.addAll( NewActiveAbilities, 0, NewActiveAbilities.size );
				NewActiveAbilities.clear();
			}

			if (toBeProcessedList.size == 0 && !hasAbilitiesToUpdate())
			{
				if (player.tasks.size > 0)
				{
					turnCount = 1;
				}
				else
				{
					turnCount = 3;
				}
			}
		}
		else if (turnCount == 3)
		{
			saveCounter++;
			if (saveCounter == 10)
			{
				Global.save();
				saveCounter = 0;
			}

			turnCount = 100;
		}
	}

	private boolean processEntityTurn( GameEntity e )
	{
		if ( e.HP <= 0 )
		{
			return true;
		}

		e.updateShadowCast();

		// If entity can take action
		if ( e.actionDelayAccumulator > 0 )
		{
			// If no tasks queued, process the ai
			if ( e.tasks.size == 0 )
			{
				e.AI.setData( "SpawnPos", e.spawnPos );
				e.AI.update( e );
			}

			// If a task is queued, process it
			if ( e.tasks.size > 0 )
			{
				e.updatedAbilityDam = false;
				e.updatedAbilityHeal = false;

				AbstractTask task = e.tasks.removeIndex( 0 );
				for ( GameEventHandler handler : e.getAllHandlers() )
				{
					handler.onTask( e, task );
				}

				if ( !task.cancel )
				{
					task.processTask( e );
				}

				if (task instanceof TaskMove)
				{
					for (AbilityTree ab : e.slottedAbilities)
					{
						if (ab != null)
						{
							ab.current.current.onMove();
						}
					}
				}
				else if (task instanceof TaskAttack)
				{
					for (AbilityTree ab : e.slottedAbilities)
					{
						if (ab != null)
						{
							ab.current.current.onAttack();
						}
					}
				}
				else if (task instanceof TaskWait)
				{
					for (AbilityTree ab : e.slottedAbilities)
					{
						if (ab != null)
						{
							ab.current.current.onWait();
						}
					}
				}

				if ( !(task instanceof TaskMove ) )
				{
					for (int x = 0; x < e.size; x++)
					{
						for (int y = 0; y < e.size; y++)
						{
							e.tile[x][y].prevEntity = e;
						}
					}
				}

				float actionCost = task.cost * e.getActionDelay();
				e.actionDelayAccumulator -= actionCost * e.getActionDelay();

				e.tile[0][0].processFieldEffectsForEntity( e, actionCost );
			}
			else
			{
				e.actionDelayAccumulator -= e.getActionDelay();
			}
		}

		return e.actionDelayAccumulator <= 0;
	}

	// endregion Process
	// ####################################################################//
	// region Getters

	private void getAllEntitiesToBeProcessed( float cost )
	{
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				GameEntity e = Grid[x][y].entity;
				if ( e != null && e != player && e.tile[0][0] == Grid[x][y] )
				{
					if ( e.tile[0][0].visible )
					{
						e.seen = true;
					}
					if ( !e.seen )
					{
						continue;
					}

					if ( Math.min( Math.abs( x - player.tile[0][0].x ), Math.abs( y - player.tile[0][0].y ) ) > 25 )
					{
						continue;
					}

					e.update( cost );

					if ( e.actionDelayAccumulator > 0 || e.tasks.size > 0 )
					{
						toBeProcessedList.add( e );
					}
				}
			}
		}

		for ( ActiveAbility aa : ActiveAbilities )
		{
			aa.updateAccumulators( cost );
		}
	}

	public final GameTile[][] getGrid()
	{
		return Grid;
	}

	public final GameTile getGameTile( Point pos )
	{
		return getGameTile( pos.x, pos.y );
	}

	public final Entity getEntityWithUID( String UID )
	{
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				GameTile tile = getGameTile( x, y );

				if ( tile.environmentEntity != null )
				{
					if ( tile.environmentEntity.UID.equals( UID ) ) { return tile.environmentEntity; }
				}

				if ( tile.entity != null )
				{
					if ( tile.entity.UID.equals( UID ) ) { return tile.entity; }
				}
			}
		}

		return null;
	}

	public final GameTile getGameTile( int x, int y )
	{
		if ( x < 0 || x >= width || y < 0 || y >= height ) { return null; }

		return Grid[ x ][ y ];
	}

	public final void getAllFields( Array<Field> list )
	{
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				if ( Grid[x][y].hasFields )
				{
					for ( FieldLayer layer : FieldLayer.values() )
					{
						Field field = Grid[x][y].fields.get( layer );
						if ( field != null )
						{
							list.add( field );
						}
					}
				}
			}
		}
	}

	public final void getAllEntities( Array<GameEntity> list )
	{
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				if ( Grid[x][y].entity != null && Grid[x][y].entity.tile[0][0] == Grid[x][y] )
				{
					list.add( Grid[x][y].entity );
				}
			}
		}
	}

	public final void getAllEnvironmentEntities( Array<EnvironmentEntity> list )
	{
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				if ( Grid[x][y].environmentEntity != null && Grid[x][y].environmentEntity.tile[0][0] == Grid[x][y] )
				{
					list.add( Grid[x][y].environmentEntity );
				}
			}
		}
	}

	public final void getAllEnvironmentEntitiesToBeProcessed( Array<EnvironmentEntity> list )
	{
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				if ( Grid[x][y].environmentEntity != null && Grid[x][y].environmentEntity.tile[0][0] == Grid[x][y] )
				{
					if ( Math.min( Math.abs( x - player.tile[0][0].x ), Math.abs( y - player.tile[0][0].y ) ) > 25 )
					{
						continue;
					}

					list.add( Grid[x][y].environmentEntity );
				}
			}
		}
	}

	private boolean hasAbilitiesToUpdate()
	{
		if ( ActiveAbilities.size > 0 )
		{
			for ( ActiveAbility aa : ActiveAbilities )
			{
				if ( aa.needsUpdate() ) { return true; }
			}
		}

		return false;
	}

	private boolean enemyVisible()
	{
		boolean enemy = false;

		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				if ( Grid[x][y].visible && Grid[x][y].entity != null )
				{
					if ( !Grid[x][y].entity.isAllies( player ) )
					{
						enemy = true;
						break;
					}
				}
			}
			if ( enemy )
			{
				break;
			}
		}

		return enemy;
	}

	private boolean hasActiveEffects()
	{
		boolean activeEffects = false;

		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				if ( !Grid[x][y].visible )
				{
					continue;
				}

				if ( Grid[x][y].spriteEffects.size > 0 )
				{
					activeEffects = true;
					break;

				}

				{
					Entity e = Grid[x][y].entity;
					if ( e != null )
					{
						boolean active = hasActiveEffects( e );
						if ( active )
						{
							activeEffects = true;
							break;
						}
					}
				}

				{
					Entity e = Grid[x][y].environmentEntity;
					if ( e != null )
					{
						boolean active = hasActiveEffects( e );
						if ( active )
						{
							activeEffects = true;
							break;
						}
					}
				}
			}

			if ( activeEffects )
			{
				break;
			}
		}

		return activeEffects;
	}

	private boolean hasActiveEffects( Entity e )
	{
		if ( e.extraUIHP > 0 ) { return true; }

		if ( e.tile[0][0].spriteEffects.size > 0 ) { return true; }

		boolean activeEffects = false;

		if ( e.sprite != null && e.sprite.spriteAnimation != null )
		{
			activeEffects = true;
		}

		return activeEffects;
	}

	// endregion Getters
	// ####################################################################//
	// region Misc

	public void addActiveAbility( ActiveAbility aa )
	{
		NewActiveAbilities.add( aa );
	}

	public void playEntitySound()
	{
		Array<GameEntity> validEntities = new Array<GameEntity>(  );

		Array<GameEntity> temp = new Array<GameEntity>(  );
		getAllEntities( temp );

		for (GameEntity ge : temp)
		{
			if (ge.HP > 0 && ge.sprite.sound != null)
			{
				if (Vector2.dst2( ge.tile[0][0].x, ge.tile[0][0].y, player.tile[0][0].x, player.tile[0][0].y ) < 100)
				{
					validEntities.add( ge );
				}
			}
		}

		if (validEntities.size > 0)
		{
			GameEntity chosen = validEntities.random();
			chosen.sprite.sound.play( chosen.tile[0][0] );
		}
	}

	public void advance( float delta )
	{
		updateAccumulator += delta;
		enemySoundAccumulator += delta;

		if (enemySoundAccumulator > 10)
		{
			playEntitySound();
			enemySoundAccumulator -= 10 + MathUtils.random() * 10;
		}

		player.updateShadowCast();
		updateVisibleTiles();
		lightList.clear();

		int playerViewRange = player.getVariable( Statistic.PERCEPTION );
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				GameTile tile = Grid[x][y];

				if ( tile.visible )
				{
					updateSpritesForTile( tile, delta );
					updateSpriteEffectsForTile( tile, delta );
					updatePopupsForTile( tile, delta );
					updateExtraUIHPForTile( tile, delta );
				}
				else
				{
					clearEffectsForTile( tile );
				}

				cleanUpDeadForTile( tile );

				tile.light.set( tile.ambientColour );
				if ( affectedByDayNight )
				{
					tile.light.mul( Global.DayNightFactor );
					tile.light.a = 1;
				}
				getLightsForTile( tile, lightList, playerViewRange );
			}
		}

		if ( ActiveAbilities.size > 0 )
		{
			for ( ActiveAbility aa : ActiveAbilities )
			{
				aa.getSprite().update( delta );

				if ( aa.light != null )
				{
					for ( GameTile t : aa.AffectedTiles )
					{
						int lx = t.x;
						int ly = t.y;
						if ( checkLightCloseEnough( lx, ly, (int) aa.light.baseIntensity, player.tile[0][0].x, player.tile[0][0].y, playerViewRange ) )
						{
							Light light = aa.light.copy();
							light.lx = lx;
							light.ly = ly;
							light.copied = true;
							lightList.add( light );
						}
					}
				}
			}
		}

		for ( AbilityTree a : player.slottedAbilities )
		{
			if ( a != null && a.current.current instanceof ActiveAbility )
			{
				ActiveAbility aa = (ActiveAbility) a.current.current;

				aa.source = player.tile[0][0];
				aa.hasValidTargets = aa.getValidTargets().size > 0;
			}
		}

		calculateLight( delta, lightList );

		for ( RepeatingSoundEffect sound : ambientSounds )
		{
			sound.update( delta );
		}
	}

	// endregion Misc
	// ####################################################################//
	// region Data

	public static final long SEENID = "seen".hashCode();
	public static final long UNSEENID = "unseen".hashCode();

	private int saveCounter = 0;
	private int turnCount = -1;

	private Array<ActiveAbility> NewActiveAbilities = new Array<ActiveAbility>( false, 16 );

	public boolean affectedByDayNight = false;
	private Array<GameEntity> toBeProcessedList = new Array<GameEntity>( false, 16 );
	private Array<Light> lightList = new Array<Light>( false, 16 );
	private Array<EnvironmentEntity> tempEnvironmentEntityList = new Array<EnvironmentEntity>( false, 16 );
	private Array<Field> tempFieldList = new Array<Field>( false, 16 );
	private Array<Light> tempLightList = new Array<Light>( false, 16 );
	private float updateDeltaStep = 0.05f;
	private float updateAccumulator;

	private Array<Point> shadowCastStore = new Array<Point>(  );

	private static final EnumBitflag<Passability> ItemDropPassability = new EnumBitflag<Passability>( Passability.WALK, Passability.ENTITY );

	public final ShadowCastCache visibilityData = new ShadowCastCache();
	private final Color tempColour = new Color();

	public Array<ActiveAbility> ActiveAbilities = new Array<ActiveAbility>( false, 16 );
	public Array<RepeatingSoundEffect> ambientSounds = new Array<RepeatingSoundEffect>();

	public String bgmName;
	public String fileName;
	public int depth;
	public long seed;
	public Array<DFPRoom> requiredRooms;

	public String UID;
	public GameEntity player;
	public boolean inTurn = false;

	public boolean isVisionRestricted = true;
	public Color Ambient = new Color( 0.1f, 0.1f, 0.3f, 1.0f );
	public Sprite background;

	public GameTile[][] Grid;
	public int width;
	public int height;

	public float enemySoundAccumulator;

	public static final SoundInstance pickupXPSound = SoundInstance.getSound( "PickupXP" );
	public static final SoundInstance pickupHPSound = SoundInstance.getSound( "PickupHP" );
	public static final SoundInstance pickupItemSound = SoundInstance.getSound( "PickupItem" );
	public static final SoundInstance entityDeathSound = SoundInstance.getSound( "EntityDeath" );

	// endregion Data
	// ####################################################################//
}
