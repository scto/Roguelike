package Roguelike.GameEvent.Constant;

import java.util.HashMap;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import Roguelike.Global;
import Roguelike.Global.ElementType;
import Roguelike.Global.Statistic;
import Roguelike.Util.FastEnumMap;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader.Element;

import exp4j.Helpers.EquationHelper;

public final class ConstantEvent
{
	public FastEnumMap<Statistic, String> equations = new FastEnumMap<Statistic, String>( Statistic.class );
	private String[] reliesOn;

	public void parse( Element xml )
	{
		reliesOn = xml.getAttribute( "ReliesOn", "" ).toLowerCase().split( "," );

		for ( int i = 0; i < xml.getChildCount(); i++ )
		{
			Element sEl = xml.getChild( i );

			if ( sEl.getName().toUpperCase().equals( "ATK" ) )
			{
				for ( ElementType el : ElementType.values() )
				{
					String expanded = sEl.getText().trim().toLowerCase();
					expanded = expanded.replaceAll( "(?<!_)atk", el.Attack.toString().toLowerCase() );

					equations.put( el.Attack, expanded );
				}
			}
			else if ( sEl.getName().toUpperCase().equals( "DEF" ) )
			{
				for ( ElementType el : ElementType.values() )
				{
					String expanded = sEl.getText().trim().toLowerCase();
					expanded = expanded.replaceAll( "(?<!_)def", el.Defense.toString().toLowerCase() );

					equations.put( el.Defense, expanded );
				}
			}
			else
			{
				Statistic el = Statistic.valueOf( sEl.getName().toUpperCase() );
				equations.put( el, sEl.getText().trim().toLowerCase() );
			}
		}
	}

	public int getStatistic( HashMap<String, Integer> variableMap, Statistic stat )
	{
		String eqn = equations.get( stat );

		if ( eqn == null ) { return 0; }

		if ( Global.isNumber( eqn ) )
		{
			return Integer.parseInt( eqn );
		}
		else
		{
			if ( reliesOn != null )
			{
				for ( String name : reliesOn )
				{
					if ( !variableMap.containsKey( name ) )
					{
						variableMap.put( name, 0 );
					}
				}
			}

			ExpressionBuilder expB = EquationHelper.createEquationBuilder( eqn );
			EquationHelper.setVariableNames( expB, variableMap, "" );

			Expression exp = EquationHelper.tryBuild( expB );
			if ( exp == null ) { return 0; }

			EquationHelper.setVariableValues( exp, variableMap, "" );

			int val = (int) exp.evaluate();

			return val;
		}
	}

	public void putStatistic( Statistic stat, String eqn )
	{
		equations.put( stat, eqn );
	}

	public static ConstantEvent load( Element xml )
	{
		ConstantEvent ce = new ConstantEvent();

		ce.parse( xml );

		return ce;
	}

	public Array<String> toString( HashMap<String, Integer> variableMap )
	{
		Array<String> lines = new Array<String>();

		{
			int val = getStatistic( variableMap, Statistic.MAXHP );
			if ( val > 0 )
			{
				String line = "MaxHP " + val;
				lines.add( line );
			}
		}

		for ( ElementType el : ElementType.values() )
		{
			if ( equations.containsKey( el.Attack ) )
			{
				int atkVal = getStatistic( variableMap, el.Attack );

				if ( atkVal > 0 )
				{
					String line = Global.capitalizeString( el.toString() ) + " attack ";
					line += "[" + el.toString() + "] ";
					line += atkVal;

					line += "[]";

					lines.add( line );
				}
			}
		}

		for ( ElementType el : ElementType.values() )
		{
			if ( equations.containsKey( el.Defense ) )
			{
				int defVal = getStatistic( variableMap, el.Defense );

				if ( defVal > 0 )
				{
					String line = Global.capitalizeString( el.toString() ) + " defense ";
					line += "[" + el.toString() + "] ";
					line += defVal;

					line += "[]";

					lines.add( line );
				}
			}
		}

		return lines;
	}
}
