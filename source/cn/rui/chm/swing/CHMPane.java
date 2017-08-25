/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : CHMPane.java
 * Created	: 2007-3-1
 * ****************************************************************************
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA  02111-1307, USA.
 * *****************************************************************************
 */
package cn.rui.chm.swing;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Stack;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import cn.rui.chm.DataFormatException;
import cn.rui.chm.protocol.chm.Handler;

public class CHMPane extends JPanel implements HyperlinkListener {

	public static final Icon BACKWARD = new ImageIcon(CHMPane.class.getResource("resource/backward.png"));
	
	public static final Icon FORWARD = new ImageIcon(CHMPane.class.getResource("resource/forward.png"));
	
	public static final Icon HOME = new ImageIcon(CHMPane.class.getResource("resource/home.png"));
	
	public static final Icon FONTSIZE = new ImageIcon(CHMPane.class.getResource("resource/fontsize.png"));
	
	public static final Icon LINK = new ImageIcon(CHMPane.class.getResource("resource/link.png"));
	
	public static final Cursor WAIT = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
	
	public static final Cursor DEFAULT = Cursor.getDefaultCursor();
	
	JTextPane siteMap;
	SiteMapFactory factory;
	JTextPane content;
	URL baseURL;
	Visit home;
	
	JToolBar toolbar;
	Stack<Visit> forwards = new Stack<Visit>();
	Stack<Visit> backwards = new Stack<Visit>();
	JButton homeBtn;
	JButton backwardBtn;
	JButton forwardBtn;
	JButton fontSizeBtn;
	JButton fontBtn;
	int fontSize;
	JScrollPane contentScroll;

	public CHMPane(String filepath) throws DataFormatException, IOException{
		super(new BorderLayout());
		baseURL = Handler.open(filepath);
 
		createToolbar();
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, 
				new JScrollPane(siteMap = new JTextPane(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
				contentScroll = new JScrollPane(content = new JTextPane(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
		split.setDividerLocation(200);
		split.setDividerSize(2);
		add(split, BorderLayout.CENTER);
		
		factory = new SiteMapFactory(this);
		siteMap.setEditorKitForContentType("text/html", new HTMLEditorKit() {
			public ViewFactory getViewFactory() {
				return factory;
			}
		});
		siteMap.setPage(baseURL);
		siteMap.setEditable(false);
		siteMap.addHyperlinkListener(this);
		siteMap.getDocument().addDocumentListener(factory);
		
		content.setEditorKitForContentType("text/html", new HTMLEditorKit() {
			// this method is copied from its super.createDefaultDocument()
			// since I can't find another way
			
			public Document createDefaultDocument() {
				StyleSheet styles = getStyleSheet();
				StyleSheet ss = new StyleSheet() {
					public Font getFont(AttributeSet a) {
						Font font = super.getFont(a);
						/* TODO is the derive method resource consuming?
						 * may be we should use a cache for the derived fonts
						 * */
						font = font.deriveFont((float) (font.getSize() + fontSize * 4));
						return font;
					}
				};
				
				ss.addStyleSheet(styles);
				HTMLDocument doc = new HTMLDocument(ss);
				doc.setParser(getParser());
				
				/* we disable async load, since we manage it ourselves */
				doc.setAsynchronousLoadPriority(-1); // originally 4
				doc.setTokenThreshold(100);
				return doc;
			}
		});
		content.setEditable(false);
		content.addHyperlinkListener(this);
		registerKeyStrokeActions();
	}

	private void registerKeyStrokeActions() {
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), 
				HTMLEditorKit.pageDownAction);
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), 
				HTMLEditorKit.pageDownAction);
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), 
				HTMLEditorKit.endAction);
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), 
				HTMLEditorKit.beginAction);
		// move content page unit up
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), 
				"unit-up");
		content.getActionMap().put("unit-up", new VerticalUnitAction(-1));
		// move content page unit down
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), 
				"unit-down");
		content.getActionMap().put("unit-down", new VerticalUnitAction(1));
		// navigate backward
		content.getActionMap().put("move-backward", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (backwardBtn.isEnabled()) backwardBtn.doClick();
			}
		});
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, true), 
				"move-backward");
		// navigate forward
		content.getActionMap().put("move-forward", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (forwardBtn.isEnabled())	forwardBtn.doClick();
			}
		});
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK, true), 
				"move-forward");
		// increase font size
		content.getActionMap().put("increase-fontsize", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (fontSizeBtn.isEnabled() && fontSize < 2) {
					fontSize ++;
					setContentPage(new Visit(content.getPage(), content.getVisibleRect()), fontSizeBtn);
				}
			}
		});
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK, true), 
				"increase-fontsize");
		// decrease font size
		content.getActionMap().put("decrease-fontsize", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (fontSizeBtn.isEnabled() && fontSize > 0) {
					fontSize --;
					setContentPage(new Visit(content.getPage(), content.getVisibleRect()), fontSizeBtn);
				}
			}
		});
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK, true), 
				"decrease-fontsize");
		// to homepage
		content.getActionMap().put("move-home", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (homeBtn.isEnabled()) setContentPage(home, null);
			}
		});
		content.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), 
				"move-home");
	}

	private void createToolbar() {
		add(toolbar = new JToolBar(), BorderLayout.NORTH); {
			toolbar.setFloatable(false);
			toolbar.add(homeBtn = new JButton(HOME));
			homeBtn.setBorder(BorderFactory.createEtchedBorder());
			homeBtn.setEnabled(false);
			homeBtn.setToolTipText("Homepage");
			homeBtn.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e) {
					setContentPage(home, null);
				}
			});
			toolbar.add(Box.createHorizontalStrut(3));
			toolbar.add(backwardBtn = new JButton(BACKWARD));
			backwardBtn.setBorder(BorderFactory.createEtchedBorder());
			backwardBtn.setEnabled(false);
			backwardBtn.setToolTipText("Move backward");
			backwardBtn.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e) {
					if (backwards.size() > 0)
						setContentPage(backwards.pop(), backwardBtn);
				}
			});
			toolbar.add(Box.createHorizontalStrut(3));
			toolbar.add(forwardBtn = new JButton(FORWARD));
			forwardBtn.setBorder(BorderFactory.createEtchedBorder());
			forwardBtn.setEnabled(false);
			forwardBtn.setToolTipText("Move forward");
			forwardBtn.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e) {
					if (forwards.size() > 0)
						setContentPage(forwards.pop(), forwardBtn);
				}
			});
			toolbar.add(Box.createHorizontalStrut(3));
			toolbar.add(fontSizeBtn = new JButton(FONTSIZE));
			fontSizeBtn.setBorder(BorderFactory.createEtchedBorder());
			fontSizeBtn.setEnabled(false);
			fontSizeBtn.setToolTipText("Scale font");
			fontSizeBtn.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e) {
					fontSize = (fontSize + 1) %  3;
					setContentPage(new Visit(content.getPage(), content.getVisibleRect()), fontSizeBtn);
				}
			});
			
			toolbar.add(Box.createHorizontalGlue());
			JLabel label = new JLabel(LINK);
			toolbar.add(label);
			label.setFont(label.getFont().deriveFont(Font.PLAIN));
			label.setToolTipText("Visit project homepage");
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter(){
				public void mouseClicked(MouseEvent e) {
					try {
						launchURL(new URL("http://powermanja.sourceforge.net"));
					} catch (MalformedURLException e1) {
					}
				}				
			});
		}
	}
	
	public void close() {
		try {
			Handler.close(baseURL);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void hyperlinkUpdate(HyperlinkEvent e) {
		if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())){
			URL url = e.getURL();
			if ("chm".equals(url.getProtocol()) ) {
				// check content types
				String file = url.getFile();
				int i = file.lastIndexOf('.');
				if (i >= 0) {
					String ext = file.substring(i).toLowerCase();
					if (".gif".equals(ext)|| ".jpg".equals(ext) || ".png".equals(ext)) {
						showImage(url);
						return;
					}
				} 
				// TODO navigate back, using /#TOPICS?
				setContentPage(e.getURL());
			} else { // try open the url in OS's default browser
				launchURL(url);
			}
		}
	}
	
	private void showImage(URL url) {
		final JFrame frame = new JFrame(url.getFile() + " - Double click to close");
		JLabel label = new JLabel(new ImageIcon(url));
		frame.getContentPane().add(new JScrollPane(label));
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		label.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) 
						&& e.getClickCount() > 1)
					frame.dispose();
			}
		});
		frame.pack();
		Rectangle bounds = frame.getBounds();
		Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
		bounds.x = (size.width - bounds.width) / 2;
		bounds.y = (size.height - bounds.height ) / 2;
		frame.setBounds(bounds);
		frame.setVisible(true);
	}

	/**
	 * Launch url in operating system way
	 */
	private void launchURL(URL url) {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("windows")) {
				if (os.contains("98") || os.contains("me")) {
					Runtime.getRuntime().exec(
						new String[]{"command.exe", "/c", "start", url.toURI().toASCIIString()});
				} else { // 2000, XP, 2003
					Runtime.getRuntime().exec(
						new String[]{"cmd.exe", "/c", "start", url.toURI().toASCIIString()});
				}
			} else if (os.contains("mac")) {
				Runtime.getRuntime().exec(
						new String[]{"/bin/sh", "-c", "open", url.toURI().toASCIIString()});
			} else { // UNIX, Linux
				Runtime.getRuntime().exec(
						new String[]{"/bin/sh", "-c", "open", url.toURI().toASCIIString()});
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(CHMPane.this, e.getMessage(),
					e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
		}
	}

	synchronized void setContentPage(String path) {
		try {
			URL url = new URL(baseURL, path);
			setContentPage(url);
		} catch (MalformedURLException e) {
			JOptionPane.showMessageDialog(CHMPane.this, e.getMessage(),
					e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
		}
	}

	synchronized void setContentPage(URL url) {
		setContentPage(new Visit(url, null), null);
	}
	
	private synchronized void setContentPage(final Visit visit, final JButton btn) {
		backwardBtn.setEnabled(false);
		forwardBtn.setEnabled(false);
		homeBtn.setEnabled(false);
		fontSizeBtn.setEnabled(false);
		toolbar.setCursor(WAIT);
		siteMap.setCursor(WAIT);
		Visit old = new Visit(content.getPage(), content.getVisibleRect());
		if ( old.url != null && ! visit.equals(old) ) {
			if (btn == null) {
				backwards.push(old);
				forwards.clear();
			} else if (btn == backwardBtn) {
				forwards.push(old);
			} else if (btn == forwardBtn) {
				backwards.push(old);
			}
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					// according to http://www.swingwiki.org/workaround:jeditorpane_html_refresh
					if (btn == fontSizeBtn)
						content.getDocument().putProperty( // this enables the URL refresh
							Document.StreamDescriptionProperty, baseURL);

					content.setPage(visit.url);
					if (visit.visibleRect != null)
						content.scrollRectToVisible(visit.visibleRect);
					content.requestFocus(); // for its key listeners
					if (home == null) home = visit; // the first page is the home url
				} catch (IOException e) {
					JOptionPane.showMessageDialog(CHMPane.this, e.getMessage(),
							e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
				} finally {
					backwardBtn.setEnabled(backwards.size() > 0);
					forwardBtn.setEnabled(forwards.size() > 0);
					homeBtn.setEnabled(! home.equals(visit.url));
					// if (! fontSizeBtn.isEnabled())
					//	adjustFont(fontSize);
					fontSizeBtn.setEnabled(true);
					toolbar.setCursor(DEFAULT);
					siteMap.setCursor(DEFAULT);
				}
			}		
		});
	}
	
	void printElement(String t, Element elem) {
		System.out.print(t + elem.getName());
		for (Enumeration en = elem.getAttributes().getAttributeNames(); en.hasMoreElements(); ) {
			Object att = en.nextElement();
			System.out.print(", "+ att + "=" + elem.getAttributes().getAttribute(att));
		}
		System.out.println();
		for (int i = 0; i < elem.getElementCount(); i ++ ){
			printElement(t + "\t", elem.getElement(i));
		}
	}
	
	class Visit {
		URL url;
		Rectangle visibleRect;
		
		public Visit(URL url, Rectangle visibleRect) {
			this.url = url;
			this.visibleRect = visibleRect;
		}
		
		public boolean equals(Visit that) {
			return this.url.equals(that.url) 
				&& that.visibleRect.equals(this.visibleRect);
		}
	}

	/**
	 * This action is copied from DefaultEditorKit.VerticalPageAction,
	 * only modify the getScrollableBlockIncrement to getScrollableUnitIncrement
	 */
    class VerticalUnitAction extends AbstractAction {

        /**
         * Direction to scroll, 1 is down, -1 is up.
         */
        private int direction;

    	/** Create this object with the appropriate identifier. */
    	public VerticalUnitAction(int direction) {
            this.direction = direction;
    	}

    	/** The operation to perform when this action is triggered. */
        public void actionPerformed(ActionEvent e) {
    		Rectangle visible = content.getVisibleRect();
            Rectangle newVis = new Rectangle(visible);
            int selectedIndex = content.getCaretPosition();
            int scrollAmount = content.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, direction); 
            int initialY = visible.y;
            Caret caret = content.getCaret();
            Point magicPosition = caret.getMagicCaretPosition();
            int yOffset;   

            if (selectedIndex != -1) {
                try {
                    Rectangle dotBounds = content.modelToView(selectedIndex);
                    int x = (magicPosition != null) ? magicPosition.x : dotBounds.x;
                    // fix for 4697612 
                    int h = dotBounds.height;
                    yOffset = direction * (int)Math.ceil(scrollAmount / (double)h) * h; 
                    newVis.y = constrainY(content, initialY + yOffset, yOffset);                        

                    int newIndex;

                    if (visible.contains(dotBounds.x, dotBounds.y)) {
                    // Dot is currently visible, base the new
                    // location off the old, or
                    newIndex = content.viewToModel(
                            new Point(x, constrainY(content,
                                      dotBounds.y + yOffset, 0)));
                    }
                    else {
                        // Dot isn't visible, choose the top or the bottom
                        // for the new location.
                        if (direction == -1) {
                            newIndex = content.viewToModel(new Point(
                                x, newVis.y));
                        }
                        else {
                            newIndex = content.viewToModel(new Point(
                                x, newVis.y + visible.height));
                        }
                    }
                    newIndex = constrainOffset(content, newIndex);
                    if (newIndex != selectedIndex) {
                        // Make sure the new visible location contains
                        // the location of dot, otherwise Caret will
                        // cause an additional scroll.
                        adjustScrollIfNecessary(content, newVis, initialY,
                                                newIndex);
//                        if (select) {
//                            content.moveCaretPosition(newIndex);
//                        }
//                        else {
                            content.setCaretPosition(newIndex);
//                        }
                    }
                } catch (BadLocationException ble) { }
            } else {
                yOffset = direction * scrollAmount;
                newVis.y = constrainY(content, initialY + yOffset, yOffset);
            }
            if (magicPosition != null) {
                caret.setMagicCaretPosition(magicPosition);
            }
    		content.scrollRectToVisible(newVis);
    	}

        /**
         * Makes sure <code>y</code> is a valid location in
         * <code>target</code>.
         */
        private int constrainY(JTextComponent target, int y, int vis) {
            if (y < 0) {
                y = 0;
            }
            else if (y + vis > target.getHeight()) {
                y = Math.max(0, target.getHeight() - vis);
            }
            return y;
        }

        /**
         * Ensures that <code>offset</code> is a valid offset into the
         * model for <code>text</code>.
         */
        private int constrainOffset(JTextComponent text, int offset) {
            Document doc = text.getDocument();

            if ((offset != 0) && (offset > doc.getLength())) {
                offset = doc.getLength();
            }
            if (offset  < 0) {
                offset = 0;
            }
            return offset;
        }

        /**
         * Adjusts the rectangle that indicates the location to scroll to
         * after selecting <code>index</code>.
         */
        private void adjustScrollIfNecessary(JTextComponent text,
                                             Rectangle visible, int initialY,
                                             int index) {
            try {
                Rectangle dotBounds = text.modelToView(index);

                if (dotBounds.y < visible.y ||
                       (dotBounds.y > (visible.y + visible.height)) ||
                       (dotBounds.y + dotBounds.height) >
                       (visible.y + visible.height)) {
                    int y;

                    if (dotBounds.y < visible.y) {
                        y = dotBounds.y;
                    }
                    else {
                        y = dotBounds.y + dotBounds.height - visible.height;
                    }
                    if ((direction == -1 && y < initialY) ||
                                        (direction == 1 && y > initialY)) {
                        // Only adjust if won't cause scrolling upward.
                        visible.y = y;
                    }
                }
            } catch (BadLocationException ble) {}
        }
    }
	
	public static void main(String[]args) throws Exception {
		String filename = null;
		if (args.length == 0) {
			JFileChooser fc = new JFileChooser(new File(""));
			fc.setFileFilter(new FileFilter() {
				public boolean accept(File f) {
					return f.isDirectory() || f.getName().toLowerCase().endsWith(".chm");
				}
				public String getDescription() {
					return "Compiled HTML File (*.chm)";
				}
			});
			if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
				filename = fc.getSelectedFile().getAbsolutePath();
		} else {
			filename = args[0];
		}
		if (filename == null) {
			System.err.println("usage: java " + CHMPane.class.getName() + " <chm file name>");
			System.exit(1);
		}
		JFrame frame = new JFrame("CHMPane " + filename);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		frame.setIconImage(((ImageIcon)LINK).getImage());
		frame.getContentPane().add(new CHMPane(filename));
		// center the frame on the desktop
		frame.setSize(800, 600);
		Rectangle bounds = frame.getBounds();
		Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
		bounds.x = (size.width - bounds.width) / 2;
		bounds.y = (size.height - bounds.height ) / 2;
//		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		frame.setBounds(bounds);
		frame.setResizable(true);
		frame.setVisible(true);
	}
}
