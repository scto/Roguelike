package Roguelike.Entity.Tasks;

import Roguelike.Global;
import Roguelike.Global.Direction;
import Roguelike.Entity.GameEntity;
import Roguelike.Sprite.SpriteAnimation.MoveAnimation;
import Roguelike.Sprite.SpriteAnimation.MoveAnimation.MoveEquation;
import Roguelike.Tiles.GameTile;

public class TaskMove extends AbstractTask
{
	Direction dir;

	public TaskMove( Direction dir )
	{
		if ( !Global.CanMoveDiagonal && !dir.isCardinal() )
		{
			throw new RuntimeException( "Invalid move direction: " + dir.toString() );
		}

		this.dir = dir;
	}

	@Override
	public void processTask( GameEntity obj )
	{
		// Collect data
		GameTile oldTile = obj.tile[0][0];

		int newX = oldTile.x + dir.getX();
		int newY = oldTile.y + dir.getY();

		boolean canMove = obj.canMove;

		if ( canMove )
		{
			for ( int x = 0; x < obj.size; x++ )
			{
				for ( int y = 0; y < obj.size; y++ )
				{
					GameTile newTile = oldTile.level.getGameTile( newX + x, newY + y );

					if ( newTile.entity != null && newTile.entity != obj )
					{
						// Swap positions if possible
						if ( obj.size == 1 && obj.isAllies( newTile.entity ) )
						{
							if ( obj.canSwap && obj.canMove && newTile.entity.canMove )
							{
								int[] diff1 = oldTile.addGameEntity( newTile.entity );
								int[] diff = newTile.addGameEntity( obj );

								oldTile.entity.sprite.spriteAnimation = new MoveAnimation( 0.15f, diff1, MoveEquation.LINEAR );
								oldTile.entity.tasks.add( new TaskWait(  ) );
								obj.sprite.spriteAnimation = new MoveAnimation( 0.15f, diff, MoveEquation.LINEAR );

								canMove = false;
							}
							else
							{
								canMove = false;
								break;
							}
						}
						else
						{
							canMove = false;
							break;
						}
					}
					else if ( !newTile.getPassable( obj.getTravelType(), obj ) )
					{
						canMove = false;
						break;
					}
				}
			}
		}

		if ( canMove )
		{
			GameTile newTile = oldTile.level.getGameTile( newX, newY );
			int[] diff = newTile.addGameEntity( obj );
			obj.sprite.spriteAnimation = new MoveAnimation( 0.15f, diff, MoveEquation.LINEAR );
		}
	}
}