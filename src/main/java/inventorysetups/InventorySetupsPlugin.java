/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package inventorysetups;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import inventorysetups.ui.InventorySetupPluginPanel;
import inventorysetups.ui.InventorySetupSlot;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.SpriteID;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.runepouch.Runes;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;


import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@PluginDescriptor(
		name = "Inventory Setups",
		description = "Save gear setups for specific activities"
)

@Slf4j
public class InventorySetupsPlugin extends Plugin
{

	public static final String CONFIG_GROUP = "inventorysetups";
	public static final String CONFIG_KEY = "setups";
	public static final String CONFIG_KEY_COMPACT_MODE = "compactMode";
	public static final String CONFIG_KEY_SORTING_MODE = "sortingMode";
	public static final String CONFIG_KEY_HIDE_BUTTON = "hideHelpButton";
	public static final String INV_SEARCH = "inv:";
	private static final String OPEN_SETUP_MENU_ENTRY = "Open setup";
	private static final String RETURN_TO_OVERVIEW_ENTRY = "Close current setup";
	private static final String ADD_TO_ADDITIONAL_ENTRY = "Add to Additional Filtered Items";
	private static final int NUM_INVENTORY_ITEMS = 28;
	private static final int NUM_EQUIPMENT_ITEMS = 14;
	private static final int SPELLBOOK_VARBIT = 4070;

	@Inject
	@Getter
	private Client client;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	@Getter
	private SpriteManager spriteManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	@Getter
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	@Getter
	private InventorySetupConfig config;

	@Inject
	@Getter
	private ColorPickerManager colorPickerManager;

	private InventorySetupPluginPanel panel;

	@Getter
	private ArrayList<InventorySetup> inventorySetups;

	private NavigationButton navButton;

	@Inject

	private InventorySetupsBankSearch bankSearch;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatboxItemSearch itemSearch;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	private ChatboxTextInput searchInput;

	// global filtering is allowed for any setup
	private boolean internalFilteringIsAllowed;

	@Setter
	@Getter
	private InventorySetupFilteringMode bankFilteringMode;

	private static final Varbits[] RUNE_POUCH_AMOUNT_VARBITS =
			{
					Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3
			};
	private static final Varbits[] RUNE_POUCH_RUNE_VARBITS =
			{
					Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3
			};



	private final HotkeyListener returnToSetupsHotkeyListener = new HotkeyListener(() -> config.returnToSetupsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			panel.returnToOverviewPanel(false);
		}
	};

	private final HotkeyListener filterBankHotkeyListener = new HotkeyListener(() -> config.filterBankHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupFilteringMode.ALL;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener filterInventoryHotkeyListener = new HotkeyListener(() -> config.filterInventoryHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupFilteringMode.INVENTORY;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener filterEquipmentHotkeyListener = new HotkeyListener(() -> config.filterEquipmentHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupFilteringMode.EQUIPMENT;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener filterAddItemsHotkeyListener = new HotkeyListener(() -> config.filterAddItemsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupFilteringMode.ADDITIONAL_FILTERED_ITEMS;
			triggerBankSearchFromHotKey();
		}
	};

	private void triggerBankSearchFromHotKey()
	{
		// you must wait at least one game tick otherwise
		// the bank filter will work but then go back to the previous tab.
		// For some reason this can still happen but it is very rare,
		// and only when the user clicks a tab and the hot key extremely shortly after.
		int gameTick = client.getTickCount();
		clientThread.invokeLater(() ->
		{
			int gameTick2 = client.getTickCount();
			if (gameTick2 <= gameTick)
			{
				return false;
			}

			doBankSearch();
			return true;
		});
	}

	@Provides
	InventorySetupConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InventorySetupConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_GROUP))
		{
			if (event.getKey().equals(CONFIG_KEY_COMPACT_MODE) || event.getKey().equals(CONFIG_KEY_HIDE_BUTTON) ||
				event.getKey().equals(CONFIG_KEY_SORTING_MODE))
			{
				panel.rebuild(true);
			}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		Widget bankWidget = client.getWidget(WidgetInfo.BANK_TITLE_BAR);
		if (bankWidget == null || bankWidget.isHidden())
		{
			return;
		}

		// Adds menu entries to show worn items button
		if (event.getOption().equals("Show worn items"))
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			final int oldMenuSize = menuEntries.length;
			menuEntries = Arrays.copyOf(menuEntries, oldMenuSize + inventorySetups.size() + 1);

			for (int i = 0; i < inventorySetups.size(); i++)
			{
				MenuEntry menuEntry = menuEntries[oldMenuSize + i] = new MenuEntry();
				menuEntry.setOption(OPEN_SETUP_MENU_ENTRY);
				menuEntry.setTarget(ColorUtil.prependColorTag(inventorySetups.get(inventorySetups.size() - 1 - i).getName(), JagexColors.MENU_TARGET));

				// The param will used to find the correct setup if a menu entry is clicked
				menuEntry.setIdentifier(inventorySetups.size() - 1 - i);
				menuEntry.setType(MenuAction.RUNELITE.getId());
			}

			// add menu entry to close setup
			MenuEntry menuEntryCloseSetup = menuEntries[menuEntries.length - 1] = new MenuEntry();
			menuEntryCloseSetup.setOption(RETURN_TO_OVERVIEW_ENTRY);
			menuEntryCloseSetup.setType(MenuAction.RUNELITE.getId());
			menuEntryCloseSetup.setTarget("");
			menuEntryCloseSetup.setIdentifier(0);

			client.setMenuEntries(menuEntries);
		}
		// If shift is held and item is right clicked in the bank while a setup is active,
		// add item to additional filtered items
		else if (panel.getCurrentSelectedSetup() != null
				&& event.getActionParam1() == WidgetInfo.BANK_ITEM_CONTAINER.getId()
				&& client.isKeyPressed(KeyCode.KC_SHIFT)
				&& event.getOption().equals("Examine"))
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			final int oldMenuSize = menuEntries.length;
			menuEntries = Arrays.copyOf(menuEntries, oldMenuSize + 1);

			MenuEntry menuEntryAddToAdditionalFiltered = menuEntries[menuEntries.length - 1] = new MenuEntry();
			menuEntryAddToAdditionalFiltered.setOption(ADD_TO_ADDITIONAL_ENTRY);
			menuEntryAddToAdditionalFiltered.setType(MenuAction.RUNELITE.getId());
			menuEntryAddToAdditionalFiltered.setTarget("");
			menuEntryAddToAdditionalFiltered.setIdentifier(0);
			menuEntryAddToAdditionalFiltered.setParam0(event.getActionParam0());
			menuEntryAddToAdditionalFiltered.setParam1(event.getActionParam1());

			client.setMenuEntries(menuEntries);
		}


	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// when the bank is loaded up allowing filtering again
		// this is to make it so the bank will refilter if a tab was clicked and then the player exited the bank
		if (event.getGroupId() == WidgetID.BANK_GROUP_ID)
		{
			internalFilteringIsAllowed = true;

			if (panel.getCurrentSelectedSetup() != null && panel.getCurrentSelectedSetup().isFilterBank() && isFilteringAllowed())
			{
				// start a bank search so the bank is filtered when it's opened
				doBankSearch();
			}
		}
	}

	public void switchViews(boolean compactMode)
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_COMPACT_MODE, compactMode);
	}

	public void toggleAlphabeticalMode(InventorySetupSorting mode)
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SORTING_MODE, mode);
	}

	@Override
	public void startUp()
	{
		this.internalFilteringIsAllowed = true;
		this.panel = new InventorySetupPluginPanel(this, itemManager);
		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "/inventorysetups_icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Inventory Setups")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();

		// Clicking the nav button will do a bank search
		navButton.setOnClick(this::doBankSearch);

		clientToolbar.addNavigation(navButton);
		keyManager.registerKeyListener(returnToSetupsHotkeyListener);
		keyManager.registerKeyListener(filterBankHotkeyListener);
		keyManager.registerKeyListener(filterInventoryHotkeyListener);
		keyManager.registerKeyListener(filterEquipmentHotkeyListener);
		keyManager.registerKeyListener(filterAddItemsHotkeyListener);

		bankFilteringMode = InventorySetupFilteringMode.ALL;

		// load all the inventory setups from the config file
		clientThread.invokeLater(() ->
		{
			switch (client.getGameState())
			{
				case STARTING:
				case UNKNOWN:
					return false;
			}

			loadConfig();

			SwingUtilities.invokeLater(() ->
			{
				panel.rebuild(true);
			});

			return true;
		});

	}

	public void addInventorySetup()
	{
		final String name = JOptionPane.showInputDialog(panel,
				"Enter the name of this setup.",
				"Add New Setup",
				JOptionPane.PLAIN_MESSAGE);

		// cancel button was clicked
		if (name == null)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			ArrayList<InventorySetupItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			ArrayList<InventorySetupItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			ArrayList<InventorySetupItem> runePouchData = null;
			if (checkIfContainerContainsItem(ItemID.RUNE_POUCH, inv))
			{
				runePouchData = getRunePouchData();
			}

			int spellbook = getCurrentSpellbook();

			final InventorySetup invSetup = new InventorySetup(inv, eqp, runePouchData, new HashMap<>(), name, "",
													config.highlightColor(),
													config.highlightStackDifference().ordinal(),
													config.highlightVariationDifference(),
													config.highlightDifference(),
													config.bankFilter(),
													config.highlightUnorderedDifference(),
													spellbook);
			addInventorySetupClientThread(invSetup);
		});
	}

	public void moveSetup(int invIndex, int newPosition)
	{
		// Setup is already in the specified position or is out of position
		if (invIndex == newPosition || newPosition < 0 || newPosition >= inventorySetups.size())
		{
			return;
		}
		InventorySetup setup = inventorySetups.remove(invIndex);
		inventorySetups.add(newPosition, setup);
		panel.rebuild(false);
		updateConfig();
	}

	public List<InventorySetup> filterSetups(String textToFilter)
	{
		final String textToFilterLower = textToFilter.toLowerCase();
		return inventorySetups.stream()
				.filter(i -> i.getName().toLowerCase().contains(textToFilterLower))
				.collect(Collectors.toList());
	}

	public void doBankSearch()
	{
		final InventorySetup currentSelectedSetup = panel.getCurrentSelectedSetup();
		internalFilteringIsAllowed = true;

		if (currentSelectedSetup != null && currentSelectedSetup.isFilterBank())
		{

			clientThread.invoke(() ->
			{
				client.setVarbit(Varbits.CURRENT_BANK_TAB, 0);
				bankSearch.layoutBank();

				// When tab is selected with search window open, the search window closes but the search button
				// stays highlighted, this solves that issue
				Widget bankContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
				if (bankContainer != null && !bankContainer.isHidden())
				{
					Widget searchBackground = client.getWidget(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND);
					searchBackground.setSpriteId(SpriteID.EQUIPMENT_SLOT_TILE);
				}
			});
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{

		if (event.getMenuAction() == MenuAction.RUNELITE)
		{
			if (event.getMenuOption().equals(OPEN_SETUP_MENU_ENTRY))
			{
				assert event.getId() >= 0 && event.getId() < inventorySetups.size() : "Action param out of range";

				resetBankSearch();
				panel.setCurrentInventorySetup(inventorySetups.get(event.getId()), true);
				return;
			}

			if (event.getMenuOption().equals(RETURN_TO_OVERVIEW_ENTRY))
			{
				panel.returnToOverviewPanel(false);
				return;
			}

			if (event.getMenuOption().equals(ADD_TO_ADDITIONAL_ENTRY))
			{
				// This should never be hit, as the option only appears when the panel isn't null
				if (panel.getCurrentSelectedSetup() == null)
				{
					return;
				}

				int inventoryIndex = event.getActionParam();
				ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
				if (bankContainer == null)
				{
					return;
				}
				Item[] items = bankContainer.getItems();
				if (inventoryIndex < 0 || inventoryIndex >= items.length)
				{
					return;
				}
				Item item = bankContainer.getItems()[inventoryIndex];
				if (item == null)
				{
					return;
				}

				final HashMap<Integer, InventorySetupItem> additionalFilteredItems =
						panel.getCurrentSelectedSetup().getAdditionalFilteredItems();

				// Item already exists, don't add it again
				if (!additionalFilteredItemsHasItem(item.getId(), additionalFilteredItems))
				{
					addAdditionalFilteredItem(item.getId(), additionalFilteredItems);
				}

			}

		}

		if (panel.getCurrentSelectedSetup() == null)
		{
			return;
		}

		if (event.getWidgetId() == WidgetInfo.BANK_ITEM_CONTAINER.getId() && event.getMenuOption().startsWith("View tab"))
		{
			if (config.disableBankTabBar())
			{
				event.consume();
			}
			return;
		}

		else if (panel.getCurrentSelectedSetup() != null
				&& (event.getMenuOption().startsWith("View tab") || event.getMenuOption().equals("View all items")))
		{
			internalFilteringIsAllowed = false;
			return;
		}

		if (event.getMenuOption().equals("Search") && client.getWidget(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND) != null
			&& client.getWidget(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND).getSpriteId() != SpriteID.EQUIPMENT_SLOT_SELECTED)
		{
			// This ensures that when clicking Search when tab is selected, the search input is opened rather
			// than client trying to close it first
			resetBankSearch();

			// don't allow the bank to retry a filter if the search button is clicked
			internalFilteringIsAllowed = false;
		}
	}

	private boolean additionalFilteredItemsHasItem(int itemId, final HashMap<Integer, InventorySetupItem> additionalFilteredItems)
	{
		final int processedItemId = itemManager.canonicalize(itemId);
		return additionalFilteredItems.get(processedItemId) != null;
	}

	private void addAdditionalFilteredItem(int itemId, final HashMap<Integer, InventorySetupItem> additionalFilteredItems)
	{
		// un-noted, un-placeholdered ID
		final int processedItemId = itemManager.canonicalize(itemId);

		clientThread.invokeLater(() ->
		{
			final String name = itemManager.getItemComposition(processedItemId).getName();
			final InventorySetupItem setupItem = new InventorySetupItem(processedItemId, name, 1, false);

			additionalFilteredItems.put(processedItemId, setupItem);
			updateConfig();
			panel.refreshCurrentSetup();
		});
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{

		if (event.getIndex() == 439 && client.getGameState() == GameState.LOGGED_IN)
		{
			// must be invoked later otherwise causes freezing.
			clientThread.invokeLater(() ->
			{
				panel.highlightSpellbook();
			});
		}

	}

	public void resetBankSearch()
	{
		bankSearch.reset(true);
	}

	public ArrayList<InventorySetupItem> getRunePouchData()
	{
		ArrayList<InventorySetupItem> runePouchData = new ArrayList<>();

		for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++)
		{
			int runeId = client.getVar(RUNE_POUCH_RUNE_VARBITS[i]);
			Runes rune = Runes.getRune(runeId);
			int runeAmount = rune == null ? 0 : client.getVar(RUNE_POUCH_AMOUNT_VARBITS[i]);
			String runeName = rune == null ? "" : rune.getName();
			int runeItemId = rune == null ? -1 : rune.getItemId();

			runePouchData.add(new InventorySetupItem(runeItemId, runeName, runeAmount, false));
		}

		return runePouchData;
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		String eventName = event.getEventName();

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();

		switch (eventName)
		{
			case "bankSearchFilter":
			{
				final InventorySetup currentSetup = panel.getCurrentSelectedSetup();
				if (currentSetup != null && currentSetup.isFilterBank() && isFilteringAllowed())
				{
					int itemId = intStack[intStackSize - 1];
					boolean containsItem = true;
					switch (bankFilteringMode)
					{
						case ALL:
							containsItem = setupContainsItem(currentSetup, itemId);
							break;
						case INVENTORY:
							containsItem = checkIfContainerContainsItem(itemId, currentSetup.getInventory());
							break;
						case EQUIPMENT:
							containsItem = checkIfContainerContainsItem(itemId, currentSetup.getEquipment());
							break;
						case ADDITIONAL_FILTERED_ITEMS:
							containsItem = additionalFilteredItemsHasItem(itemId, currentSetup.getAdditionalFilteredItems());
							break;
					}
					if (containsItem)
					{
						// return true
						intStack[intStackSize - 2] = 1;
					}
					else
					{
						intStack[intStackSize - 2] = 0;
					}
				}
				break;
			}
			case "getSearchingTagTab":
				// Clicking on a bank tab that isn't the first one (main tab),
				// then filtering the bank (either by selecting a setup or hotkey),
				// then clicking on "item" or "note" would cause the bank to show the tab
				// and remove the filter. This stops this from happening.
				final InventorySetup currentSetup = panel.getCurrentSelectedSetup();
				if (currentSetup != null && currentSetup.isFilterBank() && isFilteringAllowed())
				{
					intStack[intStackSize - 1] = 1;
				}
				else
				{
					intStack[intStackSize - 1] = 0;
				}
				break;
		}


	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			// Bankmain_build will reset the bank title to "The Bank of Gielinor". So apply our own title.
			if (panel.getCurrentSelectedSetup() != null && panel.getCurrentSelectedSetup().isFilterBank() && isFilteringAllowed())
			{
				String postTitle = " - ";
				switch (bankFilteringMode)
				{
					case ALL:
						postTitle += "All Items";
						break;
					case INVENTORY:
						postTitle += "Inventory";
						break;
					case EQUIPMENT:
						postTitle += "Equipment";
						break;
					case ADDITIONAL_FILTERED_ITEMS:
						postTitle += "Additional Items";
						break;
				}
				Widget bankTitle = client.getWidget(WidgetInfo.BANK_TITLE_BAR);
				bankTitle.setText("Inventory Setup <col=ff0000>" + panel.getCurrentSelectedSetup().getName() + postTitle + "</col>");
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			// cancel the current filtering if the search button is clicked
			resetBankSearch();

			// don't allow the bank to retry a filter if the search button is clicked
			internalFilteringIsAllowed = false;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			// The return value of bankmain_searching is on the stack. If we have a setup active
			// make it return true to put the bank in a searching state.
			if (panel.getCurrentSelectedSetup() != null && panel.getCurrentSelectedSetup().isFilterBank() && isFilteringAllowed())
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1; // true
			}
		}
	}

	public void updateCurrentSetup(InventorySetup setup)
	{
		int confirm = JOptionPane.showConfirmDialog(panel,
				"Are you sure you want update this inventory setup?",
				"Warning", JOptionPane.OK_CANCEL_OPTION);

		// cancel button was clicked
		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		// must be on client thread to get names
		clientThread.invokeLater(() ->
		{
			ArrayList<InventorySetupItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			ArrayList<InventorySetupItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			// copy over fuzzy attributes
			for (int i = 0; i < inv.size(); i++)
			{
				inv.get(i).setFuzzy(setup.getInventory().get(i).isFuzzy());
			}
			for (int i = 0; i < eqp.size(); i++)
			{
				eqp.get(i).setFuzzy(setup.getEquipment().get(i).isFuzzy());
			}

			ArrayList<InventorySetupItem> runePouchData = null;
			if (checkIfContainerContainsItem(ItemID.RUNE_POUCH, inv))
			{
				runePouchData = getRunePouchData();
			}

			setup.updateRunePouch(runePouchData);
			setup.updateInventory(inv);
			setup.updateEquipment(eqp);
			setup.updateSpellbook(getCurrentSpellbook());
			updateConfig();
			panel.refreshCurrentSetup();
		});
	}

	public void updateSlotFromContainer(final InventorySetupSlot slot)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to update from " + (slot.getSlotID().toString().toLowerCase() + "."),
					"Cannot Update Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);
		final boolean isFuzzy = getContainerFromSlot(slot).get(slot.getIndexInSlot()).isFuzzy();

		// must be invoked on client thread to get the name
		clientThread.invokeLater(() ->
		{
			final ArrayList<InventorySetupItem> playerContainer = getNormalizedContainer(slot.getSlotID());
			final InventorySetupItem newItem = playerContainer.get(slot.getIndexInSlot());
			newItem.setFuzzy(isFuzzy);

			// update the rune pouch data
			if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), newItem))
			{
				return;
			}

			container.set(slot.getIndexInSlot(), newItem);
			updateConfig();
			panel.refreshCurrentSetup();
		});

	}

	public void updateSlotFromSearch(final InventorySetupSlot slot, boolean allowStackable)
	{

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to search.",
					"Cannot Search for Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		itemSearch
			.tooltipText("Set slot to")
			.onItemSelected((itemId) ->
			{
				clientThread.invokeLater(() ->
				{
					int finalId = itemManager.canonicalize(itemId);

					// NOTE: the itemSearch shows items from skill guides which can be selected, which may be highlighted

					// if the item is stackable, ask for a quantity
					if (allowStackable && itemManager.getItemComposition(finalId).isStackable())
					{
						final int finalIdCopy = finalId;
						searchInput = chatboxPanelManager.openTextInput("Enter amount")
							// only allow numbers and k, m, b (if 1 value is available)
							// stop once k, m, or b is seen
							.addCharValidator(arg -> (arg >= 48 && arg <= 57) ||
														((arg == 66 || arg == 98 ||
														arg == 75 || arg == 107 ||
														arg == 77 || arg == 109) &&
														searchInput.getValue().length() > 0 &&
														!searchInput.getValue().toLowerCase().contains("k") &&
														!searchInput.getValue().toLowerCase().contains("m") &&
														!searchInput.getValue().toLowerCase().contains("b")))
							.onDone((input) ->
							{
								clientThread.invokeLater(() ->
								{
									int quantity = parseTextInputAmount(input);

									final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);
									final String itemName = itemManager.getItemComposition(finalIdCopy).getName();
									final InventorySetupItem newItem = new InventorySetupItem(finalIdCopy, itemName, quantity, container.get(slot.getIndexInSlot()).isFuzzy());

									// update the rune pouch data
									if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), newItem))
									{
										return;
									}

									container.set(slot.getIndexInSlot(), newItem);
									updateConfig();
									panel.refreshCurrentSetup();

								});
							}).build();
					}
					else
					{
						if (slot.getSlotID() == InventorySetupSlotID.ADDITIONAL_ITEMS)
						{
							final HashMap<Integer, InventorySetupItem> additionalFilteredItems =
									panel.getCurrentSelectedSetup().getAdditionalFilteredItems();
							if (!additionalFilteredItemsHasItem(finalId, additionalFilteredItems))
							{
								removeAdditionalFilteredItem(slot, additionalFilteredItems);
								addAdditionalFilteredItem(finalId, additionalFilteredItems);
								// duplicate update config and refresh setup are being called here
							}
						}
						else
						{
							final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);
							final String itemName = itemManager.getItemComposition(finalId).getName();
							final InventorySetupItem newItem = new InventorySetupItem(finalId, itemName, 1, container.get(slot.getIndexInSlot()).isFuzzy());
							// update the rune pouch data
							if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), newItem))
							{
								return;
							}
							container.set(slot.getIndexInSlot(), newItem);
						}

						updateConfig();
						panel.refreshCurrentSetup();
					}

				});
			})
			.build();
	}

	public void removeItemFromSlot(final InventorySetupSlot slot)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to remove item from the slot.",
					"Cannot Remove Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// must be invoked on client thread to get the name
		clientThread.invokeLater(() ->
		{

			if (slot.getSlotID() == InventorySetupSlotID.ADDITIONAL_ITEMS)
			{
				removeAdditionalFilteredItem(slot, panel.getCurrentSelectedSetup().getAdditionalFilteredItems());
				updateConfig();
				panel.refreshCurrentSetup();
				return;
			}

			final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);

			// update the rune pouch data
			final InventorySetupItem dummyItem = new InventorySetupItem(-1, "", 0, container.get(slot.getIndexInSlot()).isFuzzy());
			if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), dummyItem))
			{
				return;
			}

			container.set(slot.getIndexInSlot(), dummyItem);
			updateConfig();
			panel.refreshCurrentSetup();
		});
	}

	public void toggleFuzzyOnSlot(final InventorySetupSlot slot)
	{
		if (panel.getCurrentSelectedSetup() == null)
		{
			return;
		}

		final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);
		container.get(slot.getIndexInSlot()).toggleIsFuzzy();
		updateConfig();
		panel.refreshCurrentSetup();
	}

	private void removeAdditionalFilteredItem(final InventorySetupSlot slot, final HashMap<Integer, InventorySetupItem> additionalFilteredItems)
	{

		assert panel.getCurrentSelectedSetup() != null : "Current setup is null";

		final int slotID = slot.getIndexInSlot();

		// Empty slot was selected to be removed, don't do anything
		if (slotID >= additionalFilteredItems.size())
		{
			return;
		}

		int j = 0;
		Integer keyToDelete = null;
		for (final Integer key : additionalFilteredItems.keySet())
		{
			if (slotID == j)
			{
				keyToDelete = key;
				break;
			}
			j++;
		}

		additionalFilteredItems.remove(keyToDelete);

	}

	public void updateSpellbookInSetup(int newSpellbook)
	{
		assert panel.getCurrentSelectedSetup() != null : "Setup is null";
		assert newSpellbook >= 0 && newSpellbook < 5 : "New spellbook out of range";

		clientThread.invokeLater(() ->
		{
			panel.getCurrentSelectedSetup().updateSpellbook(newSpellbook);
			updateConfig();
			panel.refreshCurrentSetup();
		});

	}

	public void updateNotesInSetup(final InventorySetup setup, final String text)
	{
		clientThread.invokeLater(() ->
		{
			setup.updateNotes(text);
			updateConfig();
		});
	}

	public void removeInventorySetup(final InventorySetup setup)
	{
		int confirm = JOptionPane.showConfirmDialog(panel,
				"Are you sure you want to permanently delete this inventory setup?",
				"Warning", JOptionPane.OK_CANCEL_OPTION);

		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		inventorySetups.remove(setup);
		panel.rebuild(false);
		updateConfig();
	}

	public void updateConfig()
	{
		final Gson gson = new Gson();
		final String json = gson.toJson(inventorySetups);
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
	}

	@Subscribe
	public void onSessionOpen(SessionOpen event)
	{
		final AccountSession session = sessionManager.getAccountSession();
		if (session != null && session.getUsername() != null)
		{
			// config will have changed to new account, load it up
			clientThread.invokeLater(() ->
			{
				loadConfig();
				SwingUtilities.invokeLater(() ->
				{
					panel.rebuild(true);
				});

				return true;
			});
		}
	}

	@Subscribe
	public void onSessionClose(SessionClose event)
	{
		// config will have changed to local file
		clientThread.invokeLater(() ->
		{
			loadConfig();
			SwingUtilities.invokeLater(() ->
			{
				panel.rebuild(true);
			});

			return true;
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{

		// check to see that the container is the equipment or inventory
		ItemContainer container = event.getItemContainer();

		if (container == client.getItemContainer(InventoryID.INVENTORY))
		{
			panel.highlightInventory();
		}
		else if (container == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			panel.highlightEquipment();
		}

	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		panel.highlightInventory();
		panel.highlightEquipment();
		panel.highlightSpellbook();
	}

	// Must be called on client thread!
	public int getCurrentSpellbook()
	{
		assert client.isClientThread() : "getCurrentSpellbook must be called on Client Thread";
		return client.getVarbitValue(SPELLBOOK_VARBIT);
	}

	public ArrayList<InventorySetupItem> getNormalizedContainer(final InventorySetupSlotID id)
	{
		switch (id)
		{
			case INVENTORY:
				return getNormalizedContainer(InventoryID.INVENTORY);
			case EQUIPMENT:
				return getNormalizedContainer(InventoryID.EQUIPMENT);
			case RUNE_POUCH:
				return getRunePouchData();
			default:
				assert false : "Wrong slot ID!";
				return null;
		}
	}

	public ArrayList<InventorySetupItem> getNormalizedContainer(final InventoryID id)
	{
		assert id == InventoryID.INVENTORY || id == InventoryID.EQUIPMENT : "invalid inventory ID";

		final ItemContainer container = client.getItemContainer(id);

		ArrayList<InventorySetupItem> newContainer = new ArrayList<>();

		Item[] items = null;
		if (container != null)
		{
			items = container.getItems();
		}

		int size = id == InventoryID.INVENTORY ? NUM_INVENTORY_ITEMS : NUM_EQUIPMENT_ITEMS;

		for (int i = 0; i < size; i++)
		{
			if (items == null || i >= items.length)
			{
				// add a "dummy" item to fill the normalized container to the right size
				// this will be useful to compare when no item is in a slot
				newContainer.add(new InventorySetupItem(-1, "", 0, false));
			}
			else
			{
				final Item item = items[i];
				String itemName = "";

				// only the client thread can retrieve the name. Therefore, do not use names to compare!
				if (client.isClientThread())
				{
					itemName = itemManager.getItemComposition(item.getId()).getName();
				}
				newContainer.add(new InventorySetupItem(item.getId(), itemName, item.getQuantity(), false));
			}
		}

		return newContainer;
	}

	public void exportSetup(final InventorySetup setup)
	{
		final Gson gson = new Gson();
		final String json = gson.toJson(setup);
		final StringSelection contents = new StringSelection(json);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);

		JOptionPane.showMessageDialog(panel,
				"Setup data was copied to clipboard.",
				"Export Setup Succeeded",
				JOptionPane.PLAIN_MESSAGE);
	}

	public void importSetup()
	{
		try
		{
			final String setup = JOptionPane.showInputDialog(panel,
					"Enter setup data",
					"Import New Setup",
					JOptionPane.PLAIN_MESSAGE);

			// cancel button was clicked
			if (setup == null)
			{
				return;
			}

			final Gson gson = new Gson();
			Type type = new TypeToken<InventorySetup>()
			{

			}.getType();

			final InventorySetup newSetup  = gson.fromJson(setup, type);
			clientThread.invokeLater(() ->
			{
				if (newSetup.getRune_pouch() == null && checkIfContainerContainsItem(ItemID.RUNE_POUCH, newSetup.getInventory()))
				{
					newSetup.updateRunePouch(getRunePouchData());
				}
				if (newSetup.getNotes() == null)
				{
					newSetup.updateNotes("");
				}
				addInventorySetupClientThread(newSetup);
			});
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(panel,
					"Invalid setup data.",
					"Import Setup Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void shutDown()
	{
		resetBankSearch();
		clientToolbar.removeNavigation(navButton);
	}

	public boolean isHighlightingAllowed()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	public boolean isFilteringAllowed()
	{
		boolean allowBasedOnActivePanel = navButton.isSelected() || !config.requireActivePanelFilter();

		return internalFilteringIsAllowed && allowBasedOnActivePanel;
	}

	private ArrayList<InventorySetupItem> getContainerFromSlot(final InventorySetupSlot slot)
	{
		assert slot.getParentSetup() == panel.getCurrentSelectedSetup() : "Setup Mismatch";

		switch (slot.getSlotID())
		{
			case INVENTORY:
				return slot.getParentSetup().getInventory();
			case EQUIPMENT:
				return slot.getParentSetup().getEquipment();
			case RUNE_POUCH:
				return slot.getParentSetup().getRune_pouch();
			default:
				assert false : "Invalid ID given";
				return null;
		}
	}

	private void loadConfig()
	{
		final String storedSetups = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (Strings.isNullOrEmpty(storedSetups))
		{
			inventorySetups = new ArrayList<>();
		}
		else
		{
			try
			{
				final Gson gson = new Gson();
				Type type = new TypeToken<ArrayList<InventorySetup>>()
				{

				}.getType();

				// serialize the internal data structure from the json in the configuration
				final String json = fixOldJSONData(storedSetups);
				inventorySetups = gson.fromJson(json, type);
				clientThread.invokeLater(() ->
				{
					for (final InventorySetup setup : inventorySetups)
					{
						if (setup.getRune_pouch() == null && checkIfContainerContainsItem(ItemID.RUNE_POUCH, setup.getInventory()))
						{
							setup.updateRunePouch(getRunePouchData());
						}
						if (setup.getNotes() == null)
						{
							setup.updateNotes("");
						}
						if (setup.getAdditionalFilteredItems() == null)
						{
							setup.updateAdditionalItems(new HashMap<>());
						}
					}
				});
			}
			catch (Exception e)
			{
				inventorySetups = new ArrayList<>();
			}
		}
	}

	private void addInventorySetupClientThread(final InventorySetup newSetup)
	{
		SwingUtilities.invokeLater(() ->
		{
			inventorySetups.add(newSetup);
			panel.rebuild(true);

			updateConfig();
		});
	}

	private boolean setupContainsItem(final InventorySetup setup, int itemID)
	{
		// So place holders will show up in the bank.
		itemID = itemManager.canonicalize(itemID);

		// Check if this item (inc. placeholder) is in the additional filtered items
		if (additionalFilteredItemsHasItem(itemID, setup.getAdditionalFilteredItems()))
		{
			return true;
		}

		// check the rune pouch to see if it has the item (runes in this case)
		if (setup.getRune_pouch() != null)
		{
			if (checkIfContainerContainsItem(itemID, setup.getRune_pouch()))
			{
				return true;
			}
		}

		return checkIfContainerContainsItem(itemID, setup.getInventory()) ||
				checkIfContainerContainsItem(itemID, setup.getEquipment());
	}

	private boolean checkIfContainerContainsItem(int itemID, final ArrayList<InventorySetupItem> setupContainer)
	{
		// So place holders will show up in the bank.
		itemID = itemManager.canonicalize(itemID);

		for (final InventorySetupItem item : setupContainer)
		{
			if (getProcessedID(item.isFuzzy(), itemID) == getProcessedID(item.isFuzzy(), item.getId()))
			{
				return true;
			}
		}

		return false;
	}

	private int getProcessedID(boolean isFuzzy, int itemId)
	{
		// use fuzzy mapping if needed
		if (isFuzzy)
		{
			return ItemVariationMapping.map(itemId);
		}

		return itemId;
	}

	private boolean updateIfRunePouch(final InventorySetupSlot slot, final InventorySetupItem oldItem, final InventorySetupItem newItem)
	{

		if (ItemVariationMapping.map(newItem.getId()) == ItemID.RUNE_POUCH)
		{

			if (slot.getSlotID() != InventorySetupSlotID.INVENTORY)
			{

				SwingUtilities.invokeLater(() ->
				{
					JOptionPane.showMessageDialog(panel,
							"You can't have a Rune Pouch there.",
							"Invalid Item",
							JOptionPane.ERROR_MESSAGE);
				});

				return false;
			}

			// only display this message if we aren't replacing a rune pouch with a new rune pouch
			if (slot.getParentSetup().getRune_pouch() != null && ItemVariationMapping.map(oldItem.getId()) != ItemID.RUNE_POUCH)
			{
				SwingUtilities.invokeLater(() ->
				{
					JOptionPane.showMessageDialog(panel,
							"You can't have two Rune Pouches.",
							"Invalid Item",
							JOptionPane.ERROR_MESSAGE);
				});
				return false;
			}

			slot.getParentSetup().updateRunePouch(getRunePouchData());
		}
		else if (ItemVariationMapping.map(oldItem.getId()) == ItemID.RUNE_POUCH)
		{
			// if the old item is a rune pouch, need to update it to null 
			slot.getParentSetup().updateRunePouch(null);
		}

		return true;
	}

	private int parseTextInputAmount(String input)
	{
		// only take the first 10 characters (max amount is 2.147B which is only 10 digits)
		if (input.length() > 10)
		{
			input = input.substring(0, 10);
		}
		input = input.toLowerCase();

		char finalChar = input.charAt(input.length() - 1);
		int factor = 1;
		if (Character.isLetter(finalChar))
		{
			input = input.substring(0, input.length() - 1);
			switch (finalChar)
			{
				case 'k':
					factor = 1000;
					break;
				case 'm':
					factor = 1000000;
					break;
				case 'b':
					factor = 1000000000;
					break;
			}
		}

		// limit to max int value
		long quantityLong = Long.parseLong(input) * factor;
		int quantity = (int) Math.min(quantityLong, Integer.MAX_VALUE);
		quantity = Math.max(quantity, 1);

		return quantity;
	}

	private String fixOldJSONData(final String json)
	{
		final Gson gson = new Gson();
		JsonElement je = gson.fromJson(json, JsonElement.class);
		JsonArray ja = je.getAsJsonArray();
		for (JsonElement elem : ja)
		{
			JsonObject setup = elem.getAsJsonObject();
			// Fix old configs that had stackDifference as a boolean (before it had more options)
			if (setup.getAsJsonPrimitive("stackDifference").isBoolean())
			{
				int stackDiff = setup.get("stackDifference").getAsBoolean() ? 1 : 0;
				setup.remove("stackDifference");
				setup.addProperty("stackDifference", stackDiff);
			}
		}
		return je.toString();
	}

}
