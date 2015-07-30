package Roguelike.Entity.Tasks;

import Roguelike.Ability.ActiveAbility.ActiveAbility;
import Roguelike.Entity.GameEntity;

public class TaskUseAbility extends AbstractTask
{
	public int[] target;
	public ActiveAbility ability;
	
	public TaskUseAbility(int[] target, ActiveAbility ability)
	{
		this.target = target;
		this.ability = ability;
	}
	
	@Override
	public void processTask(GameEntity obj)
	{	
		ability.cooldownAccumulator = ability.cooldown;
		
		ActiveAbility aa = ability.copy();
		
		aa.caster = obj;
		aa.source = obj.tile;
		aa.variableMap = obj.getVariableMap();
		
		aa.lockTarget(obj.tile.level.getGameTile(target));
		
		boolean finished = aa.update();
		
		if (!finished)
		{
			obj.tile.level.addActiveAbility(aa);
		}
	}

}
