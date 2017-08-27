/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : SiteMapFactory.java
 * Created	: 2007-3-1
 * 
 * ****************************************************************************
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com)
 * http://chmpane.sourceforge.net, All Right Reserved. 
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "CHMPane" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************
 */
package cn.rui.chm.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.html.BlockView;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLEditorKit.HTMLFactory;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;


class SiteMapFactory extends HTMLFactory implements DocumentListener {

	static final Icon OPEN = new ImageIcon(CHMPane.class.getResource("resource/open.png"));
	
	static final Icon PAGE = new ImageIcon(CHMPane.class.getResource("resource/page.png"));
	
	static final Icon LIST = new ImageIcon(CHMPane.class.getResource("resource/list.png"));
	
	private JTree tree;
	
	private CHMPane pane;

	private boolean useSiteMap;
	
	public SiteMapFactory(CHMPane pane) {
		this.pane = pane;
	}

	public void changedUpdate(DocumentEvent e) {
		if (tree != null) { // expand the tree
			final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					tree.expandPath(new TreePath(root));
				}
			});
			if (root.getChildCount() > 0 ) {
				DefaultMutableTreeNode first = (DefaultMutableTreeNode) root.getChildAt(0);
				final Object obj = first.getUserObject();
				if (obj != null && obj instanceof Element) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							pane.setContentPage((String) ((Element) obj).getAttributes().getAttribute("Local"));
						}
					});
				}
			}
		}
	}

	public void insertUpdate(DocumentEvent e) {	}
	
	public void removeUpdate(DocumentEvent e) {	}

	@Override
	public View create(final Element elem) {
		AttributeSet attrs = elem.getAttributes();
		Object tag = attrs.getAttribute(StyleConstants.NameAttribute);
		if ( tag == Tag.OBJECT) {
			if ("text/site properties".equals(attrs.getAttribute(HTML.Attribute.TYPE))) {
				useSiteMap = true;
				return new TreeView(elem);
			} else if (useSiteMap && "text/sitemap".equals(attrs.getAttribute(HTML.Attribute.TYPE))) {
				return new TreeNodeView(elem);
			}
		} else if (useSiteMap && tag == Tag.UL) {
			return new EmptyBlockView(elem);
		}
		return super.create(elem);
	}
	

	class EmptyBlockView extends BlockView {
		
		public EmptyBlockView(Element elem) {
			super(elem, View.X_AXIS);
		}

		public float getPreferredSpan(int axis) {
			return 0;
		}

		public float getMinimumSpan(int axis) {
			return 0;
		}

		public float getMaximumSpan(int axis) {
			return 0;
		}

		public Shape modelToView(int pos, Shape a,
				Position.Bias b) throws BadLocationException {
			return a;
		}

		public int getNextVisualPositionFrom(int pos,
				Position.Bias b, Shape a, int direction,
				Position.Bias[] biasRet) {
			return getElement().getEndOffset();
		}
	}

	class TreeView extends ComponentView {

		DefaultMutableTreeNode root;

		public TreeView(Element elem) {
			super(elem);
		}
		
		// override this method to fix the no-horizontal-scrollbar problem 
		public float getMinimumSpan(int axis) {
			return getPreferredSpan(axis);
		}

		protected Component createComponent() {
			tree = new JTree(root = new DefaultMutableTreeNode("ROOT"));
			tree.setRootVisible(false);
			tree.setShowsRootHandles(true);
			tree.setScrollsOnExpand(true);
			tree.setCellRenderer(new DefaultTreeCellRenderer() {
				public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
					// TODO icons can be determined by element's "ImageNumber" attribute
					setOpenIcon(OPEN);
					setClosedIcon(LIST);
					setLeafIcon(PAGE);
					if (node.isRoot())
						return super.getTreeCellRendererComponent(
							tree, value, sel, expanded, leaf,	row, hasFocus);
					
					Element elem = (Element) node.getUserObject();
					Component comp = super.getTreeCellRendererComponent(tree, 
							elem.getAttributes().getAttribute("Name"), sel, expanded, leaf,
							row, hasFocus);
					comp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					comp.setBackground(Color.RED);
					return comp;
				}
			});
			tree.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e)) {
				         TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
				         if(selPath != null) {
				        	 DefaultMutableTreeNode node = 
				        		 (DefaultMutableTreeNode) selPath.getLastPathComponent();
				        	 if (node.getUserObject() instanceof Element) {
				        		 Element elem = (Element) node.getUserObject();
							     pane.setContentPage((String) elem.getAttributes().getAttribute("Local"));
				        	 }
				         }
					}
				}
			});
			return tree;
		}
		
		public void addNode(DefaultMutableTreeNode child) {
			root.add(child);
		}
	}

	class TreeNodeView extends EmptyBlockView {
		
		DefaultMutableTreeNode node;
		
		public TreeNodeView(Element elem) {
			super(elem) ;
			node = new DefaultMutableTreeNode(elem);
		}

		public void setParent(View parent) {
			super.setParent(parent);
			try {
				//object / p-implied / li      / ul            /li
				for (boolean ul = false; parent != null; parent = parent.getParent()) {
					Object tag = parent.getElement().getAttributes().getAttribute(StyleConstants.NameAttribute);
					if (tag == Tag.UL) {
						ul = true;
					} else if (tag == Tag.LI && ul) {
						// TODO using more checked/safe methods
						((TreeNodeView)parent.getView(0).getView(0).getView(0)).addNode(node);
						break;
					} else if (tag == Tag.BODY) {					
						((TreeView) parent.getView(0).getView(0).getView(0)).addNode(node);
						break;
					}
				}
			} catch (NullPointerException e) {
//				System.out.println("NULL " + getElement().getAttributes().getAttribute("Name"));
			}
		}

		public void addNode(DefaultMutableTreeNode child) {
			node.add(child);
		}
	}
}

