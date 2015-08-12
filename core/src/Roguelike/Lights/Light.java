package Roguelike.Lights;

import Roguelike.Global.Passability;
import Roguelike.Pathfinding.ShadowCaster;
import Roguelike.Tiles.GameTile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.XmlReader.Element;

public class Light
{
	private static final Array<Passability> LightPassability = new Array<Passability>(new Passability[]{Passability.LIGHT});
		
	public boolean copied;
	
	public Color colour;
	public float baseIntensity;
	public int flicker;
	public float flickerPeriod;
	
	public float actualIntensity;
	public int lx;
	public int ly;
	
	private float timeAccumulator;
	
	private int lastlx = -1;
	private int lastly = -1;
	private Array<int[]> opaqueTiles = new Array<int[]>();
	private Array<int[]> shadowCastOutput = new Array<int[]>();
	
	public Array<int[]> getShadowCast(GameTile[][] grid)
	{
		boolean recalculate = false;
		
		if (lx != lastlx || ly != lastly)
		{
			recalculate = true;
		}
		else
		{
			for (int[] pos : opaqueTiles)
			{
				GameTile tile = grid[pos[0]][pos[1]];				
				if (tile.getPassable(LightPassability))
				{
					recalculate = true; // something has moved
					break;
				}
			}
		}
		
		if (recalculate)
		{
			shadowCastOutput.clear();
			
			ShadowCaster shadow = new ShadowCaster(grid, (int)Math.ceil(baseIntensity));
			shadow.ComputeFOV(lx, ly, shadowCastOutput);
			
			// build list of opaque
			opaqueTiles.clear();
			for (int[] pos : shadowCastOutput)
			{
				GameTile tile = grid[pos[0]][pos[1]];				
				if (!tile.getPassable(LightPassability))
				{
					opaqueTiles.add(pos);
				}
			}
			lastlx = lx;
			lastly = ly;
		}
		
		return shadowCastOutput;
	}
	
	public void update(float delta)
	{
		if (flicker > 0)
		{
			timeAccumulator += delta;
			while(timeAccumulator > flickerPeriod)
			{
				timeAccumulator -= flickerPeriod;
			}
			
			float factor = 1.0f / (float)flicker;			
			float fraction = baseIntensity / 20;
			
			actualIntensity = fraction * 19;
			
			for (int i = 0; i < flicker; i++)
			{
				actualIntensity += fraction * (float)( Math.sin( timeAccumulator * (i+1) / (flickerPeriod/(2*Math.PI)) ) * factor );
			}
		}
		else
		{
			actualIntensity = baseIntensity;
		}
	}
	
	public Light copyNoFlag()
	{
		Light l = copy();
		l.copied = false;
		return l;
	}
	
	public Light copy()
	{
		Light l = Pools.obtain(Light.class);
		l.colour = new Color(colour);
		l.baseIntensity = baseIntensity;
		l.copied = true;
		l.flicker = flicker;
		l.flickerPeriod = flickerPeriod;
		l.timeAccumulator = timeAccumulator;
		
		l.lastlx = lastlx;
		l.lastly = lastly;
		l.opaqueTiles.addAll(opaqueTiles);
		l.shadowCastOutput.addAll(shadowCastOutput);
				
		return l;
	}
	
	public static Light load(Element xml)
	{
		Light l = new Light();
		
		Element colourElement = xml.getChildByName("Colour");
		Color colour = Color.WHITE;
		if (colourElement != null)
		{
			colour = new Color();
			colour.a = 1;
			
			String rgb = colourElement.get("RGB", null);
			if (rgb != null)
			{
				String[] cols = rgb.split(",");
				colour.r = Float.parseFloat(cols[0]) / 255.0f;
				colour.g = Float.parseFloat(cols[1]) / 255.0f;
				colour.b = Float.parseFloat(cols[2]) / 255.0f;
			}
			
			colour.r = colourElement.getFloat("Red", colour.r);
			colour.g = colourElement.getFloat("Green", colour.g);
			colour.b = colourElement.getFloat("Blue", colour.b);
			colour.a = colourElement.getFloat("Alpha", colour.a);
			
			l.colour = colour;
		}
		
		l.baseIntensity = xml.getFloat("Intensity");
		l.flicker = xml.getInt("Flicker", 0);
		l.flickerPeriod = xml.getFloat("FlickerPeriod", 1);
		l.timeAccumulator = MathUtils.random() * l.flickerPeriod;
		
		return l;
	}
}